#!/system/bin/sh

FLAG=/data/adb/k90pm_nps/autostart.enabled
PACKAGE=com.k90pm.npsclient
COMPONENT=com.k90pm.npsclient/com.duanlab.npsclient.AutoStartBroadReceiver
ACTION=com.k90pm.npsclient.ROOT_BOOT

[ -f "$FLAG" ] || exit 0

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

sleep 5

launch_nps() {
    am broadcast --include-stopped-packages -a "$ACTION" -n "$COMPONENT" >/dev/null 2>&1
    log -t K90PM-NPS "root boot guard requested service start"
}

launch_nps

while [ -f "$FLAG" ]; do
    sleep 60
    [ -f "$FLAG" ] || exit 0
    if ! pidof "$PACKAGE" >/dev/null 2>&1 || ! pidof libnpc.so >/dev/null 2>&1; then
        launch_nps
    fi
done
