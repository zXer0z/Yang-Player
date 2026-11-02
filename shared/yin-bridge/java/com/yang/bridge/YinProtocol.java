package com.yang.bridge;

import android.os.*;

/** Binder carries control/FDs only. PCM travels through a bounded socket, never a Parcel. */
public final class YinProtocol {
    private YinProtocol() {}
    public static final String PACKAGE = "local.v60.player";
    public static final String SERVICE = "local.v60.player.YinService";
    public static final String DESCRIPTOR = "local.v60.player.YIN_AUDIO_V1";
    public static final int VERSION = 1;
    public static final int HELLO = 1, OPEN = 2, STATE = 3, STATUS = 4, EOS = 5, CLOSE = 6;
    public static Bundle call(IBinder binder, int command, Bundle arguments) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR); data.writeBundle(arguments == null ? new Bundle() : arguments);
            if (!binder.transact(command, data, reply, 0)) throw new RemoteException("Yin no admite este protocolo");
            reply.readException();
            Bundle result = reply.readBundle(YinProtocol.class.getClassLoader());
            if (result == null) throw new RemoteException("Yin no devolvió respuesta");
            return result;
        } finally { reply.recycle(); data.recycle(); }
    }
}
