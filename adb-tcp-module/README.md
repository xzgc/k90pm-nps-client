# Persistent ADB TCP 5555

This KernelSU/Magisk module keeps adbd listening on TCP port 5555.

It:

- waits for Android boot completion;
- sets `service.adb.listen_addrs=tcp:5555`;
- restarts adbd;
- checks the listener every 30 seconds and recreates it if needed.

Install the module ZIP from KernelSU or Magisk, then reboot.

Check the listener:

```sh
su -c 'getprop service.adb.listen_addrs; ss -lnt | grep :5555'
```

Log file:

```text
/data/adb/adb_tcp_keeper.log
```

To stop the keeper after the next reboot, disable or uninstall the module. To stop
the current keeper immediately, create the disable flag, restore USB-only ADB,
and restart adbd:

```sh
su -c 'touch /data/adb/adb_tcp_keeper.disabled; setprop service.adb.listen_addrs usb; setprop service.adb.tcp.port 0; stop adbd; start adbd'
```
