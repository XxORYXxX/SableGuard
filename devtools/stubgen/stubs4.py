import os,sys
ROOT=sys.argv[1]
S={}
def c(fqn, body): S[fqn]=body
c('net.neoforged.neoforgespi.language.IModInfo','public interface IModInfo { org.apache.maven.artifact.versioning.ArtifactVersion getVersion(); String getModId(); String getDisplayName(); IModFileInfo getOwningFile(); }')
c('net.neoforged.neoforgespi.language.IModFileInfo','public interface IModFileInfo { net.neoforged.neoforgespi.locating.IModFile getFile(); }')
c('net.neoforged.neoforgespi.locating.IModFile','public interface IModFile { java.nio.file.Path getFilePath(); String getFileName(); }')
c('net.neoforged.fml.loading.moddiscovery.ModInfo','public class ModInfo implements net.neoforged.neoforgespi.language.IModInfo { public String getModId(){return null;} public org.apache.maven.artifact.versioning.ArtifactVersion getVersion(){return null;} public String getDisplayName(){return null;} public ModFileInfo getOwningFile(){return null;} }')
c('net.neoforged.fml.loading.moddiscovery.ModFileInfo','public class ModFileInfo implements net.neoforged.neoforgespi.language.IModFileInfo { public ModFile getFile(){return null;} }')
c('net.neoforged.fml.loading.moddiscovery.ModFile','public class ModFile implements net.neoforged.neoforgespi.locating.IModFile { public java.nio.file.Path getFilePath(){return null;} public String getFileName(){return null;} }')
c('net.neoforged.fml.loading.FMLPaths','public enum FMLPaths { GAMEDIR, MODSDIR, CONFIGDIR, FMLCONFIG; public java.nio.file.Path get(){return null;} }')
c('net.neoforged.fml.ModList','public class ModList { public static ModList get(){return null;} public boolean isLoaded(String id){return false;} public java.util.Optional<? extends net.neoforged.fml.ModContainer> getModContainerById(String id){return null;} public java.util.List<net.neoforged.neoforgespi.language.IModInfo> getMods(){return null;} }')
c('net.neoforged.neoforge.event.entity.living.LivingDeathEvent','public class LivingDeathEvent extends net.neoforged.bus.api.Event { public net.minecraft.world.entity.LivingEntity getEntity(){return null;} }')
c('net.neoforged.neoforge.event.entity.player.PlayerEvent','public abstract class PlayerEvent extends net.neoforged.bus.api.Event { public net.minecraft.world.entity.player.Player getEntity(){return null;} public static class PlayerLoggedInEvent extends PlayerEvent {} }')
c('net.neoforged.neoforge.client.event.RegisterClientCommandsEvent','public class RegisterClientCommandsEvent extends net.neoforged.bus.api.Event { public com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> getDispatcher(){return null;} }')
c('net.minecraft.core.particles.ParticleOptions','public interface ParticleOptions {}')
c('net.minecraft.client.player.LocalPlayer','public class LocalPlayer extends net.minecraft.world.entity.player.Player { public void displayClientMessage(net.minecraft.network.chat.Component c, boolean b){} }')
c('net.minecraft.client.Minecraft','public class Minecraft { public net.minecraft.client.multiplayer.ClientLevel level; public net.minecraft.client.player.LocalPlayer player; public net.minecraft.world.phys.HitResult hitResult; public static Minecraft getInstance(){return null;} }')
SB='dev.ryanhcode.sable.'
c(SB+'companion.math.BoundingBox3ic','public interface BoundingBox3ic { int minX(); int minY(); int minZ(); int maxX(); int maxY(); int maxZ(); int volume(); }')
c(SB+'sublevel.plot.LevelPlot','public abstract class LevelPlot { public net.minecraft.world.level.ChunkPos plotPos; public dev.ryanhcode.sable.companion.math.BoundingBox3ic getBoundingBox(){return null;} public java.util.Collection<PlotChunkHolder> getLoadedChunks(){return null;} }')
c(SB+'api.SubLevelHelper','public final class SubLevelHelper { public static java.util.Collection<dev.ryanhcode.sable.sublevel.ServerSubLevel> getLoadingDependencyChain(dev.ryanhcode.sable.sublevel.ServerSubLevel s){return null;} }')
c(SB+'sublevel.ClientSubLevel','public class ClientSubLevel extends SubLevel { public Object getRenderData(){return null;} public boolean isFinalized(){return false;} }')
c(SB+'api.sublevel.ClientSubLevelContainer','public class ClientSubLevelContainer extends SubLevelContainer { public java.util.List<dev.ryanhcode.sable.sublevel.ClientSubLevel> getAllSubLevels(){return null;} }')
CBC='net.felis.cbc_ballistics.'
c(CBC+'item.custom.RangefinderItem','public class RangefinderItem extends net.minecraft.world.item.Item { public void rangeFind(net.minecraft.world.item.ItemStack s, net.minecraft.world.entity.player.Player p){} }')
c(CBC+'util.ParticleHelper','public class ParticleHelper { public static void line(net.minecraft.world.level.Level l, net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, Colour c, float f){} public static void dot(net.minecraft.world.level.Level l, net.minecraft.world.phys.Vec3 a, Colour c, float f){} public static void Circle(net.minecraft.world.level.Level l, net.minecraft.world.phys.Vec3 a, Colour c, float r, float x, float y, float s){} public enum Colour { YELLOW, BLACK, WHITE, BLUE, GREEN, RED } }')
c(CBC+'util.Utils','public class Utils { public static String formatPos(net.minecraft.world.phys.Vec3 v){return null;} }')
for fqn,body in S.items():
    pkg,name=fqn.rsplit('.',1)
    p=os.path.join(ROOT,*pkg.split('.'),name+'.java')
    os.makedirs(os.path.dirname(p),exist_ok=True)
    open(p,'w').write('package %s;\n%s\n'%(pkg,body))
