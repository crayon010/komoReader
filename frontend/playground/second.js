// ============================================================
//  1. 心跳类 — 独立管理 ping/pong 心跳
// ============================================================
class HeartBeat {
    /**
     * @param {Object} options
     * @param {number} options.interval  - 心跳间隔 ms（默认 10000）
     * @param {number} options.timeout   - pong 超时 ms（默认 5000）
     * @param {number} options.maxMiss   - 最多连续丢包次数（默认 2）
     */
    constructor(options = {}) {
        this.interval = options.interval ?? 10000
        this.timeout  = options.timeout  ?? 5000
        this.maxMiss  = options.maxMiss  ?? 2

        this._missCount    = 0         // 连续丢包计数
        this._timer        = null      // 心跳定时器
        this._pongTimer    = null      // 等 pong 的定时器
        this._sendPing     = null      // 由外部注入: () => void
        this._onDead       = null      // 心跳死亡回调
        this._pongReceived = false     // 本轮是否收到 pong
        this._running      = false
    }

    /** 注入"发 ping"的动作（由 WsClient 提供） */
    setSendPing(fn)  { this._sendPing = fn }

    /** 注册心跳死亡回调 */
    onDead(fn)       { this._onDead = fn }

    /** 外部收到 pong 时调用 */
    handlePong()     { this._pongReceived = true }

    /** 启动心跳 */
    start() {
        if (this._running) return
        this._running    = true
        this._missCount  = 0
        this._schedule()
    }

    /** 停止心跳（重置所有计时器） */
    stop() {
        this._running = false
        clearTimeout(this._timer)
        clearTimeout(this._pongTimer)
        this._timer     = null
        this._pongTimer = null
    }

    /** 重置丢包计数（外部可在连接建立后调用） */
    reset() {
        this._missCount  = 0
        this._pongReceived = false
    }

    // ---- 内部 ----

    _schedule() {
        if (!this._running) return
        clearTimeout(this._timer)
        this._timer = setTimeout(() => this._beat(), this.interval)
    }

    _beat() {
        if (!this._running) return

        // 发 ping
        this._pongReceived = false
        if (this._sendPing) this._sendPing()

        // 等 pong
        clearTimeout(this._pongTimer)
        this._pongTimer = setTimeout(() => {
            if (!this._pongReceived) {
                this._missCount++
                if (this._missCount >= this.maxMiss) {
                    this.stop()
                    if (this._onDead) this._onDead()
                    return
                }
            } else {
                this._missCount = 0
            }
            this._schedule()  // 下一轮
        }, this.timeout)
    }
}

// ============================================================
//  2. 断线重连类 — 独立管理重连策略
// ============================================================
class Reconnect {
    /**
     * @param {Object} options
     * @param {number} options.baseDelay  - 首次重连延迟 ms（默认 1000）
     * @param {number} options.maxDelay   - 最大重连延迟 ms（默认 30000）
     * @param {number} options.factor     - 退避倍率（默认 2）
     * @param {number} options.maxRetries - 最大重试次数，Infinity 为无限（默认 Infinity）
     */
    constructor(options = {}) {
        this.baseDelay  = options.baseDelay  ?? 1000
        this.maxDelay   = options.maxDelay   ?? 30000
        this.factor     = options.factor     ?? 2
        this.maxRetries = options.maxRetries ?? Infinity

        this._retryCount = 0
        this._timer      = null
        this._onBeforeReconnect = null  // 重连前回调 → 返回 WebSocket 实例
        this._onReconnected     = null  // 重连成功后回调
    }

    /** 注入"执行一次连接"的动作 */
    onBeforeReconnect(fn) { this._onBeforeReconnect = fn }

    /** 注册重连成功回调 */
    onReconnected(fn)     { this._onReconnected     = fn }

    /** 触发重连流程 */
    start() {
        if (this._timer) return  // 已在重连中
        this._retryCount = 0
        this._try()
    }

    /** 停止重连（重置状态） */
    stop() {
        clearTimeout(this._timer)
        this._timer      = null
        this._retryCount = 0
    }

    /** 外部可调用：连接成功时重置计数 */
    reset() {
        this._retryCount = 0
    }

    // ---- 内部 ----

    _try() {
        if (this._retryCount >= this.maxRetries) {
            console.error('[Reconnect] 已达最大重试次数')
            return
        }

        const delay = Math.min(
            this.baseDelay * Math.pow(this.factor, this._retryCount),
            this.maxDelay
        )
        this._retryCount++

        console.log(`[Reconnect] ${delay}ms 后第 ${this._retryCount} 次重连`)

        this._timer = setTimeout(() => {
            this._timer = null
            if (this._onBeforeReconnect) {
                const ws = this._onBeforeReconnect()
                if (!ws || ws.readyState !== WebSocket.OPEN) return
            }
            if (this._onReconnected) this._onReconnected()
        }, delay)
    }
}

// ============================================================
//  3. 消息接发类 — 独立管理 reqId 匹配 / 超时
// ============================================================
class Message {
    /**
     * @param {Object} options
     * @param {number} options.timeout - 请求默认超时 ms（默认 10000）
     */
    constructor(options = {}) {
        this.timeout = options.timeout ?? 10000

        /** @type {Map<number, {resolve, reject, timer}>} */
        this._pending = new Map()
        this._reqId   = 1

        /** @type {WebSocket | null}  外部注入 */
        this._ws      = null

        /** 外部注入的消息处理（在 raw onmessage 之前调用） */
        this._onBeforeMessage = null
    }

    /** 注入 WebSocket 实例 */
    setWs(ws)              { this._ws = ws }

    /** 默认超时修改 */
    setDefaultTimeout(ms)  { this.timeout = ms }

    /** 外部 onmessage 进来时调用，处理 reqId 匹配 */
    handleMessage(rawData) {
        let res
        try               { res = JSON.parse(rawData) }
        catch (_)         { if (this._onBeforeMessage) this._onBeforeMessage(rawData); return }

        const { reqId, data, err } = res
        if (reqId !== undefined && this._pending.has(reqId)) {
            const item = this._pending.get(reqId)
            clearTimeout(item.timer)
            this._pending.delete(reqId)
            if (err) item.reject(new Error(err))
            else     item.resolve(data)
        } else {
            // 非 reqId 消息，交给外部
            if (this._onBeforeMessage) this._onBeforeMessage(rawData)
        }
    }

    /** 发送请求（自动附加 reqId），返回 Promise */
    request(payload) {
        if (!this._ws || this._ws.readyState !== WebSocket.OPEN) {
            return Promise.reject(new Error('WebSocket 未连接'))
        }
        const reqId = this._reqId++
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                this._pending.delete(reqId)
                reject(new Error(`请求 #${reqId} 超时`))
            }, this.timeout)

            this._pending.set(reqId, { resolve, reject, timer })
            this._ws.send(JSON.stringify({ reqId, data: payload }))
        })
    }

    /** 发送纯消息（不期望回复） */
    send(payload) {
        if (!this._ws || this._ws.readyState !== WebSocket.OPEN) {
            console.warn('[Message] WebSocket 未连接，发送取消')
            return
        }
        this._ws.send(JSON.stringify(payload))
    }

    /** 连接断开时拒绝所有待处理请求 */
    rejectAll(err = '连接已断开') {
        for (const [reqId, item] of this._pending) {
            clearTimeout(item.timer)
            item.reject(new Error(err))
        }
        this._pending.clear()
    }

    /** 注册"非 reqId 消息"处理 */
    onMessage(fn) { this._onBeforeMessage = fn }
}

// ============================================================
//  4. 封装类 — 组合三个模块，对外暴露 await request()
// ============================================================
class WsClient {
    /**
     * @param {string} url               - WebSocket 地址
     * @param {Object} [opts]
     * @param {Object} [opts.heartbeat]  - 心跳参数  { interval, timeout, maxMiss }
     * @param {Object} [opts.reconnect]  - 重连参数  { baseDelay, maxDelay, factor, maxRetries }
     * @param {Object} [opts.message]    - 消息参数  { timeout }
     */
    constructor(url, opts = {}) {
        this.url = url

        // 三个独立模块
        this.heartbeat = new HeartBeat(opts.heartbeat)
        this.reconnect = new Reconnect(opts.reconnect)
        this.message   = new Message(opts.message)

        // 桥接
        this._setupBridge()

        // 启动连接
        this._createWs()
    }

    // ---- 对外 API ----

    /**
     * 发送请求并等待响应（核心方法）
     * @param  {*}  data
     * @return {Promise<*>}
     */
    request(data) {
        return this.message.request(data)
    }

    /**
     * 发送单向消息（不等待响应）
     * @param {*} data
     */
    send(data) {
        this.message.send(data)
    }

    /** 修改请求默认超时 */
    setRequestTimeout(ms) {
        this.message.setDefaultTimeout(ms)
    }

    /** 注册推送消息回调（服务端主动推送，无 reqId） */
    onPush(fn) {
        this.message.onMessage(fn)
    }

    /** 主动关闭连接 */
    close() {
        this.heartbeat.stop()
        this.reconnect.stop()
        if (this._ws) {
            this._ws.close()
            this._ws = null
        }
        this.message.rejectAll('客户端主动关闭')
    }

    // ---- 内部 ----

    _setupBridge() {
        // 心跳：注入 ping 发送动作 & 死亡回调
        this.heartbeat.setSendPing(() => {
            if (this._ws && this._ws.readyState === WebSocket.OPEN) {
                this._ws.send(JSON.stringify({ type: 'ping' }))
            }
        })
        this.heartbeat.onDead(() => {
            console.warn('[WsClient] 心跳超时，触发重连')
            if (this._ws) {
                this._ws.close()
                this._ws = null
            }
            this.reconnect.start()
        })

        // 重连：注入"创建连接" & "连接成功"回调
        this.reconnect.onBeforeReconnect(() => {
            return this._createWs()
        })
        this.reconnect.onReconnected(() => {
            console.log('[WsClient] 重连成功')
        })
    }

    _createWs() {
        this.heartbeat.stop()
        this.reconnect.stop()

        const ws = new WebSocket(this.url)

        ws.onopen = () => {
            console.log('[WsClient] 连接成功')
            this.message.setWs(ws)
            this.reconnect.reset()
            this.heartbeat.start()
        }

        ws.onmessage = (e) => {
            const raw = e.data

            // 如果是 pong 响应，交给心跳
            try {
                const obj = JSON.parse(raw)
                if (obj.type === 'pong') {
                    this.heartbeat.handlePong()
                    return
                }
            } catch (_) { /* 非 JSON，继续 */ }

            // 否则交给消息模块匹配
            this.message.handleMessage(raw)
        }

        ws.onclose = () => {
            console.warn('[WsClient] 连接断开')
            this.heartbeat.stop()
            this.message.rejectAll('连接断开')
            // 触发重连
            this.reconnect.start()
        }

        ws.onerror = (err) => {
            console.error('[WsClient] 异常', err)
        }

        this._ws = ws
        return ws
    }
}

// ---- 导出（ESM） ----
export { WsClient, HeartBeat, Reconnect, Message }
