import os,sys
ROOT=sys.argv[1]
S={}
def c(fqn, body):
    S[fqn]=body
# ---------------- mixin annotations (full attribute sets)
c('org.spongepowered.asm.mixin.Mixin','public @interface Mixin { Class<?>[] value() default {}; String[] targets() default {}; int priority() default 1000; boolean remap() default true; }')
c('org.spongepowered.asm.mixin.Pseudo','public @interface Pseudo {}')
c('org.spongepowered.asm.mixin.Overwrite','public @interface Overwrite { boolean remap() default true; }')
c('org.spongepowered.asm.mixin.Shadow','public @interface Shadow { String prefix() default "shadow$"; boolean remap() default true; String[] aliases() default {}; }')
c('org.spongepowered.asm.mixin.injection.At','public @interface At { String id() default ""; String value(); String slice() default ""; Shift shift() default Shift.NONE; int by() default 0; String[] args() default {}; String target() default ""; Desc desc() default @Desc(""); int ordinal() default -1; int opcode() default -1; boolean remap() default true; enum Shift { NONE, BEFORE, AFTER, BY } }')
c('org.spongepowered.asm.mixin.injection.Desc','public @interface Desc { String value(); }')
c('org.spongepowered.asm.mixin.injection.Slice','public @interface Slice { String id() default ""; At from() default @At("HEAD"); At to() default @At("TAIL"); }')
inj='String[] method() default {}; Slice[] slice() default {}; At[] at() default {}; boolean remap() default true; int require() default -1; int expect() default 1; int allow() default -1; String constraints() default "";'
c('org.spongepowered.asm.mixin.injection.Inject','public @interface Inject { String id() default ""; '+inj+' boolean cancellable() default false; LocalCapture locals() default LocalCapture.NO_CAPTURE; }')
c('org.spongepowered.asm.mixin.injection.callback.LocalCapture','public enum LocalCapture { NO_CAPTURE, PRINT, CAPTURE_FAILSOFT, CAPTURE_FAILHARD, CAPTURE_FAILEXCEPTION }')
c('org.spongepowered.asm.mixin.injection.ModifyArg','public @interface ModifyArg { String[] method() default {}; Slice slice() default @Slice; At at(); int index() default -1; boolean remap() default true; int require() default -1; int expect() default 1; int allow() default -1; String constraints() default ""; }')
c('org.spongepowered.asm.mixin.injection.ModifyVariable','public @interface ModifyVariable { String[] method() default {}; Slice slice() default @Slice; At at(); boolean print() default false; int ordinal() default -1; int index() default -1; String[] name() default {}; boolean argsOnly() default false; boolean remap() default true; int require() default -1; int expect() default 1; int allow() default -1; String constraints() default ""; }')
c('org.spongepowered.asm.mixin.injection.Redirect','public @interface Redirect { String[] method() default {}; Slice slice() default @Slice; At at(); boolean remap() default true; int require() default -1; int expect() default 1; int allow() default -1; String constraints() default ""; }')
c('org.spongepowered.asm.mixin.injection.callback.CallbackInfo','public class CallbackInfo { public void cancel(){} public boolean isCancelled(){return false;} public boolean isCancellable(){return false;} }')
c('org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable','public class CallbackInfoReturnable<R> extends CallbackInfo { public void setReturnValue(R r){} public R getReturnValue(){return null;} }')
c('com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation','public @interface WrapOperation { String[] method() default {}; org.spongepowered.asm.mixin.injection.At[] at() default {}; org.spongepowered.asm.mixin.injection.Slice[] slice() default {}; boolean remap() default true; int require() default -1; int expect() default 1; int allow() default -1; }')
c('com.llamalad7.mixinextras.injector.wrapoperation.Operation','public interface Operation<R> { R call(Object... args); }')
c('com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod','public @interface WrapMethod { String[] method(); boolean remap() default true; int require() default -1; int expect() default 1; int allow() default -1; }')
c('com.llamalad7.mixinextras.sugar.Local','public @interface Local { boolean print() default false; int ordinal() default -1; int index() default -1; String[] name() default {}; boolean argsOnly() default false; }')
c('com.llamalad7.mixinextras.injector.ModifyExpressionValue','public @interface ModifyExpressionValue { String[] method(); org.spongepowered.asm.mixin.injection.At[] at(); boolean remap() default true; int require() default -1; }')
# ---------------- joml
c('org.joml.Quaterniondc','public interface Quaterniondc { double x(); double y(); double z(); double w(); }')
# ---------------- fastutil extras
c('it.unimi.dsi.fastutil.objects.ObjectArraySet','public class ObjectArraySet<K> extends java.util.AbstractSet<K> implements ObjectSet<K> { public ObjectArraySet(){} public ObjectArraySet(int c){} public java.util.Iterator<K> iterator(){return null;} public int size(){return 0;} }')
c('it.unimi.dsi.fastutil.ints.Int2ObjectMap','public interface Int2ObjectMap<V> extends java.util.Map<Integer,V> { V get(int k); V put(int k, V v); V remove(int k); boolean containsKey(int k); }')
c('it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap','public class Int2ObjectLinkedOpenHashMap<V> extends java.util.LinkedHashMap<Integer,V> implements Int2ObjectMap<V> { public V get(int k){return null;} public V put(int k, V v){return null;} public V remove(int k){return null;} public boolean containsKey(int k){return false;} }')
c('it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap','public class Int2ObjectOpenHashMap<V> extends java.util.HashMap<Integer,V> implements Int2ObjectMap<V> { public V get(int k){return null;} public V put(int k, V v){return null;} public V remove(int k){return null;} public boolean containsKey(int k){return false;} }')
# ---------------- netty
c('io.netty.buffer.ByteBuf','public abstract class ByteBuf { public abstract int readableBytes(); public abstract int readerIndex(); public abstract ByteBuf readerIndex(int i); public abstract byte getByte(int i); public abstract byte readByte(); public abstract boolean isReadable(); public abstract int writerIndex(); }')
c('io.netty.channel.ChannelHandlerContext','public interface ChannelHandlerContext {}')
c('io.netty.channel.socket.DatagramPacket','public class DatagramPacket { public io.netty.buffer.ByteBuf content(){return null;} public java.net.InetSocketAddress sender(){return null;} }')
# ---------------- night config
#   (real library compiled separately)
for fqn,body in S.items():
    pkg,name=fqn.rsplit('.',1)
    p=os.path.join(ROOT,*pkg.split('.'),name+'.java')
    os.makedirs(os.path.dirname(p),exist_ok=True)
    open(p,'w').write('package %s;\n%s\n'%(pkg,body))
print(len(S))
