#!/bin/bash
# Generates the download page index.html
VERSION=$1
DATE=$(date +"%d/%m/%Y %H:%M")

cat > index.html << EOF
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ECK Scanner</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,sans-serif;background:#1A1A2E;color:#fff;min-height:100vh;display:flex;align-items:center;justify-content:center}
.card{background:#fff;color:#333;border-radius:16px;padding:32px;max-width:400px;width:90%;text-align:center;box-shadow:0 20px 60px rgba(0,0,0,0.3)}
h1{font-size:24px;color:#1A1A2E;margin-bottom:4px}
.version{font-size:48px;font-weight:bold;color:#1B5E20;margin:16px 0}
.date{font-size:13px;color:#888;margin-bottom:24px}
.btn{display:block;background:#E94560;color:#fff;text-decoration:none;padding:16px;border-radius:8px;font-size:18px;font-weight:bold;margin-bottom:12px}
.btn:active{background:#c1374e}
.btn-alt{background:#1A1A2E}
.size{font-size:12px;color:#aaa;margin-top:8px}
</style>
</head>
<body>
<div class="card">
<h1>ECK Scanner</h1>
<p style="color:#888;font-size:13px">App para Zebra TC25</p>
<div class="version">v${VERSION}</div>
<p class="date">Actualizado: ${DATE}</p>
<a href="ECKScanner-v${VERSION}.apk" class="btn">DESCARGAR v${VERSION}</a>
<a href="https://github.com/CarlosTorrezC/eck-scanner/releases" class="btn btn-alt">Ver todas las versiones</a>
<p class="size">~5 MB | Android 8+</p>
</div>
</body>
</html>
EOF
