#!/system/bin/sh

PORT=5555
PROP_VALUE="tcp:$PORT"
LOG_FILE=/data/adb/adb_tcp_keeper.log
DISABLE_FILE=/data/adb/adb_tcp_keeper.disabled

log_message() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG_FILE"
}

is_listening() {
    ss -lnt 2>/dev/null | grep -q ":$PORT[[:space:]]"
}

start_tcp_adbd() {
    setprop service.adb.listen_addrs "$PROP_VALUE"
    setprop service.adb.tcp.port 0

    if is_listening; then
        return 0
    fi

    log_message "starting adbd with $PROP_VALUE"
    stop adbd
    sleep 1
    start adbd
    sleep 3

    if is_listening; then
        log_message "adbd is listening on $PROP_VALUE"
        return 0
    fi

    log_message "adbd did not open $PORT"
    return 1
}

[ -f "$DISABLE_FILE" ] && exit 0

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

sleep 8
start_tcp_adbd

while true; do
    sleep 30

    if [ -f "$DISABLE_FILE" ]; then
        log_message "disabled by flag"
        exit 0
    fi

    if [ "$(getprop service.adb.listen_addrs)" != "$PROP_VALUE" ] || ! is_listening; then
        start_tcp_adbd
    fi
done
