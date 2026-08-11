@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "ROOT=%~dp0"
set "TASK=VoiceNotesServer"

schtasks /Query /TN %TASK% >nul 2>&1 && schtasks /Delete /TN %TASK% /F >nul 2>&1

REM 生成隐藏启动器（避免开机弹黑窗口）
set "VBS=%ROOT%run_hidden.vbs"
(
echo Set ws = CreateObject^("Wscript.Shell"^)
echo ws.Run """%ROOT%start.bat""", 0, False
) > "%VBS%"

schtasks /Create /TN %TASK% /TR "\"%VBS%\"" /SC ONLOGON /RL LIMITED /F
echo.
echo 已注册开机自启计划任务: %TASK%  （登录 Windows 后自动在后台运行）
echo 现在立即启动服务...
wscript "%VBS%"
echo.
echo 完成！请在浏览器打开 http://127.0.0.1:8000/health 验证。
pause
