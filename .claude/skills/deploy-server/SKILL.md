---
name: deploy-server
description: >-
  Выкатить ledscheme-server на VPS dxv (руками, без CI). Применять когда
  изменился код сервера ИЛИ статическая веб-страница
  (ledscheme-server/src/main/resources/static) и это нужно опубликовать.
  Покрывает сборку fat jar, scp, бэкап/подмену/рестарт и обязательную проверку
  живого эндпоинта. Фиксирует границу ответственности на VPS.
---

# Деплой ledscheme-server на VPS dxv

## Перед началом

- **Золотое правило**: получи явное разрешение пользователя на выкладку.
- **ГРАНИЦА ОТВЕТСТВЕННОСТИ на VPS dxv (138.16.177.176)**: на ней же крутится
  отдельная система **"dxvfi"** (Node/PM2/nginx) — **никогда её не трогай**.
  Разрешены только: systemd-сервис `ledscheme-server`, Docker-контейнер
  `ledscheme-postgres`, выделенный экземпляр Caddy на `:8443`.
- SSH-алиас `dxv` настроен (`~/.ssh/config`, root, ключ `~/.ssh/id_ed25519`).

## Шаги

```bash
# 1. Собрать fat jar локально (spring-boot-maven-plugin репакует автоматически)
cd C:\Development\ledscheme-server && mvn -DskipTests package
#    -> target/ledscheme-server-0.1.0.jar

# 2. Залить во временное место (jar ~50 МБ — может уйти в фон)
scp target/ledscheme-server-0.1.0.jar dxv:/tmp/app.jar.new

# 3. Бэкап, подмена, права, рестарт
ssh dxv "cp /opt/ledscheme-server/app.jar /opt/ledscheme-server/app.jar.bak-$(date +%Y%m%d%H%M%S) \
  && cp /tmp/app.jar.new /opt/ledscheme-server/app.jar \
  && chown ledscheme:ledscheme /opt/ledscheme-server/app.jar \
  && rm /tmp/app.jar.new \
  && systemctl restart ledscheme-server \
  && sleep 5 && systemctl is-active ledscheme-server"
```

## Проверка (обязательна, не пропускать)

```bash
curl -sk https://138.16.177.176:8443/api/library/changes   # должен вернуть 200
```

`systemctl is-active` недостаточно — сервис может подняться и падать на первом
запросе. Проверяй именно живой эндпоинт.

## Детали

- Юнит: `/etc/systemd/system/ledscheme-server.service`,
  `ExecStart=/usr/bin/java -jar /opt/ledscheme-server/app.jar`.
- Env-файл `/opt/ledscheme-server/app.env` — секреты (`DB_*`, `JWT_SECRET`,
  `SERVER_ADDRESS=127.0.0.1`), **НЕ в git**.
- **Публичная веб-страница едет ВНУТРИ того же fat jar** (Spring Boot отдаёт
  `classpath:/static/`) — отдельного шага деплоя для неё нет, обычный `mvn
  package` + `scp` + рестарт подхватывает новые файлы.
- Откат: бэкапы лежат в `/opt/ledscheme-server/app.jar.bak-<timestamp>`.
- Комментарий в `application.yml` про `deploy/README.md` устарел — такого файла
  нет.

## Инфраструктура вокруг (TLS / reverse-proxy / мост)

Сервер слушает только `127.0.0.1:8081`; снаружи — выделенный Caddy на `:8443` с
самоподписанным сертификатом; клиент/админка используют certificate pinning
(`TrustedHttp.clientFor(baseUrl)`); есть мост через Cloudflare-домен
`ledschemedesigner.ru`. Полное описание и мотивация — `ARCHITECTURE.md` §5.
**Менять конфиг Caddy / ufw / Cloudflare — только по явному запросу**, это не
часть рутинного деплоя.
