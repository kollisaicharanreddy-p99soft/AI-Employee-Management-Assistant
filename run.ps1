# Helper script — ensures Maven always uses JDK 21 for this project
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$args_str = $args -join ' '
Write-Host "Using JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Cyan
Invoke-Expression "mvn $args_str"
