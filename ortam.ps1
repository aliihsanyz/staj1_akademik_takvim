# Akademik Takvim - gelistirme ortami aktivasyonu
# Kullanim:  . .\ortam.ps1
$envRoot = "C:\Users\aliih\miniconda3\envs\akademik-takvim"
$env:JAVA_HOME = "$envRoot\Library"
$env:PATH = "$envRoot\Library\bin;$envRoot\Scripts;$envRoot;C:\Program Files\PostgreSQL\18\bin;" + $env:PATH
$env:PGPASSWORD = "admin123"
Write-Host "Ortam hazir:" -ForegroundColor Green
java -version
mvn -v | Select-Object -First 1
