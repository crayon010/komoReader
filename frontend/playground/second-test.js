class HeartBeat {
    constructor(ws, optiosn = {}) {
        this.ws = ws;
        this.heartInterval = options.interval || 30000;// 30s一次心跳
        this.timeout = options.timeout || 10000; // 10s无响应判定断线
        this.timer = null;
        this.timeoutTimer = null;
        this.heartData = options.heartData || { action: 'heartbeat' }; // 心跳报文
    }

    init() {
        if (this.ws) {
            this.timer = setInterval(() => {
                if (this.ws.readyState === WebSocket.OPEN) {
                    this.ws.send(JSON.stringify(this.heartData));
                    // 心跳超时计时器
                    this.timeoutTimer = setTimeout(() => {
                        // 心跳超时，触发断开
                        this.ws.close();
                    }, this.timeout);
                }
            }, this.heartInterval);
        }
    }

    stop() {
        this.timer = null;
        this.timeoutTimer = null;
    }

}

class Reconnect {
    constructor() {

    }
}

class Message {
    constructor() {

    }
}

class WsClient {
    constructor() {
        this.url = url;
        this.ws = null;

        // 三个独立模块
        this.heartbeat = new HeartBeat(opts.heartbeat);
        this.reconnect = new Reconnect(opts.reconnect);
        this.message = new Message(opts.message);
    }

    init() {
        this.ws = new WebSocket(url);
        this.heart.ws = this.ws;
    }

    request(data) {
        return this.message.request(data);
    }
}