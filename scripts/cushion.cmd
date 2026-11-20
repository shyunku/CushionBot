@echo off
rem CushionBot status and logs from Windows (cmd / PowerShell). Runs scripts/cushion with Git Bash,
rem which forwards to the server over ssh. Examples: cushion ps, cushion logs -f, cushion logs -e -s "1 hour ago"
"%ProgramFiles%\Git\bin\bash.exe" "%~dp0cushion" %*
