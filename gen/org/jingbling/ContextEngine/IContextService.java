/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: C:\\Users\\Jing\\Documents\\GitHub\\ContextService\\src\\org\\jingbling\\ContextEngine\\IContextService.aidl
 */
package org.jingbling.ContextEngine;
public interface IContextService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.jingbling.ContextEngine.IContextService
{
private static final java.lang.String DESCRIPTOR = "org.jingbling.ContextEngine.IContextService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.jingbling.ContextEngine.IContextService interface,
 * generating a proxy if needed.
 */
public static org.jingbling.ContextEngine.IContextService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.jingbling.ContextEngine.IContextService))) {
return ((org.jingbling.ContextEngine.IContextService)iin);
}
return new org.jingbling.ContextEngine.IContextService.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_getContext:
{
data.enforceInterface(DESCRIPTOR);
java.util.List<java.lang.String> _arg0;
_arg0 = data.createStringArrayList();
java.lang.String _arg1;
_arg1 = data.readString();
java.lang.String _arg2;
_arg2 = data.readString();
java.lang.String _result = this.getContext(_arg0, _arg1, _arg2);
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_gatherTrainingData:
{
data.enforceInterface(DESCRIPTOR);
java.util.List<java.lang.String> _arg0;
_arg0 = data.createStringArrayList();
java.lang.String _arg1;
_arg1 = data.readString();
this.gatherTrainingData(_arg0, _arg1);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.jingbling.ContextEngine.IContextService
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
@Override public java.lang.String getContext(java.util.List<java.lang.String> featuresToUse, java.lang.String classifierToUse, java.lang.String contextGroup) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStringList(featuresToUse);
_data.writeString(classifierToUse);
_data.writeString(contextGroup);
mRemote.transact(Stub.TRANSACTION_getContext, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public void gatherTrainingData(java.util.List<java.lang.String> featuresToUse, java.lang.String contextGroup) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStringList(featuresToUse);
_data.writeString(contextGroup);
mRemote.transact(Stub.TRANSACTION_gatherTrainingData, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_getContext = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_gatherTrainingData = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
public java.lang.String getContext(java.util.List<java.lang.String> featuresToUse, java.lang.String classifierToUse, java.lang.String contextGroup) throws android.os.RemoteException;
public void gatherTrainingData(java.util.List<java.lang.String> featuresToUse, java.lang.String contextGroup) throws android.os.RemoteException;
}
