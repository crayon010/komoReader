@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

set PORT=1236

echo ========================================
echo  正在检查端口 %PORT% 的占用情况...
echo ========================================
echo.

set FOUND=0

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%PORT%" ^| findstr "LISTENING"') do (
    set PID=%%a
    set FOUND=1

    echo [发现] 端口 %PORT% 被 PID !PID! 占用

    for /f "tokens=1 delims= " %%b in ('tasklist /FI "PID eq !PID!" /NH') do (
        echo [进程] %%b
    )

    echo [操作] 正在结束 PID !PID! ...
    taskkill /PID !PID! /F >nul 2>&1

    if !errorlevel! equ 0 (
        echo [成功] PID !PID! 已结束
    ) else (
        echo [失败] PID !PID! 结束失败，请尝试以管理员身份运行
    )
    echo.
)

if !FOUND! equ 0 (
    echo [结果] 端口 %PORT% 当前没有被占用。
) else (
    echo ========================================
    echo  清理完成，再次检查端口状态：
    echo ========================================
    netstat -ano | findstr ":%PORT%" | findstr "LISTENING"
    if !errorlevel! neq 0 echo 端口 %PORT% 已释放。
)

echo.
pause