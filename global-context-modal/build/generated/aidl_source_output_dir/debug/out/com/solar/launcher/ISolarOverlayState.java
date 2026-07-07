/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.solar.launcher;
public interface ISolarOverlayState extends android.os.IInterface
{
  /** Default implementation for ISolarOverlayState. */
  public static class Default implements com.solar.launcher.ISolarOverlayState
  {
    @Override public boolean isSolarAlive() throws android.os.RemoteException
    {
      return false;
    }
    @Override public int policyRevision() throws android.os.RemoteException
    {
      return 0;
    }
    @Override public android.os.Bundle getPowerMenuSnapshot() throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.os.Bundle getContextMenuSnapshot(java.lang.String sessionId) throws android.os.RemoteException
    {
      return null;
    }
    @Override public boolean dispatchAction(java.lang.String sessionId, int actionIndex) throws android.os.RemoteException
    {
      return false;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.solar.launcher.ISolarOverlayState
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.solar.launcher.ISolarOverlayState interface,
     * generating a proxy if needed.
     */
    public static com.solar.launcher.ISolarOverlayState asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.solar.launcher.ISolarOverlayState))) {
        return ((com.solar.launcher.ISolarOverlayState)iin);
      }
      return new com.solar.launcher.ISolarOverlayState.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_isSolarAlive:
        {
          boolean _result = this.isSolarAlive();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_policyRevision:
        {
          int _result = this.policyRevision();
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_getPowerMenuSnapshot:
        {
          android.os.Bundle _result = this.getPowerMenuSnapshot();
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_getContextMenuSnapshot:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          android.os.Bundle _result = this.getContextMenuSnapshot(_arg0);
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_dispatchAction:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          int _arg1;
          _arg1 = data.readInt();
          boolean _result = this.dispatchAction(_arg0, _arg1);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.solar.launcher.ISolarOverlayState
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public boolean isSolarAlive() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_isSolarAlive, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int policyRevision() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_policyRevision, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.os.Bundle getPowerMenuSnapshot() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.Bundle _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPowerMenuSnapshot, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, android.os.Bundle.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.os.Bundle getContextMenuSnapshot(java.lang.String sessionId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.Bundle _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(sessionId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getContextMenuSnapshot, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, android.os.Bundle.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean dispatchAction(java.lang.String sessionId, int actionIndex) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(sessionId);
          _data.writeInt(actionIndex);
          boolean _status = mRemote.transact(Stub.TRANSACTION_dispatchAction, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_isSolarAlive = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_policyRevision = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_getPowerMenuSnapshot = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_getContextMenuSnapshot = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_dispatchAction = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
  }
  public static final java.lang.String DESCRIPTOR = "com.solar.launcher.ISolarOverlayState";
  public boolean isSolarAlive() throws android.os.RemoteException;
  public int policyRevision() throws android.os.RemoteException;
  public android.os.Bundle getPowerMenuSnapshot() throws android.os.RemoteException;
  public android.os.Bundle getContextMenuSnapshot(java.lang.String sessionId) throws android.os.RemoteException;
  public boolean dispatchAction(java.lang.String sessionId, int actionIndex) throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
