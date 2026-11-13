package net.bedrocknative.client;

import java.nio.file.Path;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.account.AccountManager;
import net.bedrocknative.client.account.AccountScreenHooks;
import net.bedrocknative.client.account.AccountWarmUp;
import net.bedrocknative.client.account.DevAccountsTest;
import net.bedrocknative.client.content.BaseDownloads;
import net.bedrocknative.client.content.BedrockBlockModels;
import net.bedrocknative.client.content.BedrockItemModel;
import net.bedrocknative.client.content.BedrockSpriteSource;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.content.BlockSlotSource;
import net.bedrocknative.client.content.DevBaseTest;
import net.bedrocknative.client.content.MoreCullingCompat;
import net.bedrocknative.client.form.BedrockForms;
import net.bedrocknative.client.inventory.BedrockChestMenu;
import net.bedrocknative.client.inventory.BedrockItemTooltips;
import net.bedrocknative.client.lobby.BedrockLobby;
import net.bedrocknative.client.lobby.DevLobbyTest;
import net.bedrocknative.client.lobby.DevOreDisconnectTest;
import net.bedrocknative.client.lobby.DevOreMenusTest;
import net.bedrocknative.client.lobby.DevOrePlayTest;
import net.bedrocknative.client.lobby.DevPartnersTest;
import net.bedrocknative.client.model.ActorPrep;
import net.bedrocknative.client.model.AttachableArmorRenderer;
import net.bedrocknative.client.model.AttachableHeldLayer;
import net.bedrocknative.client.model.BedrockActorRenderer;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockEmotes;
import net.bedrocknative.client.model.WornAttachables;
import net.bedrocknative.client.net.BedrockBridging;
import net.bedrocknative.client.net.BedrockConnector;
import net.bedrocknative.client.net.ClientReport;
import net.bedrocknative.client.net.DevConnectTest;
import net.bedrocknative.client.net.DevNetherNetLoadTest;
import net.bedrocknative.client.net.DevNetherNetTest;
import net.bedrocknative.client.net.DevPacksTest;
import net.bedrocknative.client.net.DevPrismaTest;
import net.bedrocknative.client.net.DevRestoreTest;
import net.bedrocknative.client.net.DevStartupTest;
import net.bedrocknative.client.net.DevTexturesOffline;
import net.bedrocknative.client.net.JoinWarmUp;
import net.bedrocknative.client.net.SessionKinds;
import net.bedrocknative.client.oreui.OreUi;
import net.bedrocknative.client.sound.BedrockAudio;
import net.bedrocknative.client.ui.DevJsonUiHudOffline;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.client.world.BedrockItemFrames;
import net.bedrocknative.client.world.BedrockScreenAnimations;
import net.bedrocknative.client.world.ChunkStream;
import net.bedrocknative.net.nethernet.NetherNetTransport;
import net.bedrocknative.net.nethernet.SignalingProbe;
import net.bedrocknative.net.transport.TransportRegistry;
import net.bedrocknative.perf.StartupClock;
import net.bedrocknative.protocol.CodecWarmUp;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.bedrocknative.world.item.BedrockItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.EndExtraction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.level.ItemLike;

public final class BedrockNativeClient implements ClientModInitializer {
   private static void useNetherNet() {
      try {
         Path config = FabricLoader.getInstance().getConfigDir().resolve("bedrocknative");
         TransportRegistry.useNetherNet(NetherNetTransport.create(config), new SignalingProbe());
      } catch (LinkageError | RuntimeException var1) {
         BedrockNative.LOGGER.warn("NetherNet is not available in this game ({}): RakNet and Java servers only", var1.toString());
      }
   }

   public void onInitializeClient() {
      StartupClock.step("screen hooks", () -> {
         BedrockForms.registerScreenHooks();
         BedrockEmotes.register();
         AccountScreenHooks.register();
         AccountManager.registerWindowSignIn();
         BedrockItemTooltips.register();
      });
      StartupClock.step("item model and slots", () -> {
         BedrockItemModel.register();
         BlockSlotSource.register();
         BedrockChestMenu.register();
         BedrockScreenAnimations.register();
      });
      StartupClock.step("renderers", () -> {
         EntityRendererRegistry.register(BedrockActorEntity.TYPE, BedrockActorRenderer::new);
         LevelExtractionEvents.END_EXTRACTION.register((EndExtraction)context -> BedrockActors.endFrame());
         ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> BedrockAudio.tick());
         ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> BedrockBlockModels.tick());
         ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> BedrockItemFrames.tick());
         ClientTickEvents.END_CLIENT_TICK.register(BedrockBridging::tick);
         ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> BedrockTextures.tick());
         ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> MoreCullingCompat.disableCarrierCulling());
         ChunkStream.register();
         ActorPrep.register();
         JsonUiHud.register();
         ArmorRenderer.register(new AttachableArmorRenderer(), new ItemLike[]{BedrockItems.CUSTOM});
         LivingEntityRenderLayerRegistrationCallback.EVENT.register((LivingEntityRenderLayerRegistrationCallback)(type, renderer, helper, context) -> {
            if (renderer.getModel() instanceof HumanoidModel) {
               helper.register(heldLayer(renderer));
               WornAttachables.addHeldLayerType(type);
            }
         });
         BedrockSpriteSource.register();
      });
      StartupClock.step("base download", BaseDownloads::register);
      StartupClock.step("lobby preload", BedrockLobby::preload);
      StartupClock.step("Ore UI preload", OreUi::preload);
      ClientTickEvents.END_CLIENT_TICK.register(BedrockLobby::tick);
      ClientTickEvents.END_CLIENT_TICK.register(AccountWarmUp::tick);
      ClientTickEvents.END_CLIENT_TICK.register(JoinWarmUp::tick);
      SessionKinds.register();
      StartupClock.step("NetherNet", BedrockNativeClient::useNetherNet);
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> {
         BedrockConnector.shutdown();
         ClientReport.gameClosing(client);
      });
      StartupClock.step("codec warm-up", () -> CodecWarmUp.start(StartupClock::background));
      StartupClock.clientInitDone(StartupClock.sinceJvmStart());
      String devTest = System.getProperty("bedrocknative.devTest", "");
      if (devTest.startsWith("packs")) {
         DevPacksTest.start(devTest.equals("packs-required"));
      } else if (devTest.startsWith("prisma")) {
         DevPrismaTest.start(devTest);
      } else if (devTest.equals("jsonui-hud-offline")) {
         DevJsonUiHudOffline.start();
      } else if (devTest.equals("accounts")) {
         DevAccountsTest.start();
      } else if (devTest.equals("lobby")) {
         DevLobbyTest.start();
      } else if (devTest.equals("base")) {
         DevBaseTest.start();
      } else if (devTest.equals("oremenus")) {
         DevOreMenusTest.start();
      } else if (devTest.equals("oreplay")) {
         DevOrePlayTest.start();
      } else if (devTest.equals("partners")) {
         DevPartnersTest.start();
      } else if (devTest.equals("oredisconnect")) {
         DevOreDisconnectTest.start();
      } else if (devTest.equals("textures-offline")) {
         DevTexturesOffline.start();
      } else if (devTest.equals("connect")) {
         DevConnectTest.start();
      } else if (devTest.equals("nethernet-load")) {
         DevNetherNetLoadTest.start();
      } else if (devTest.equals("nethernet")) {
         DevNetherNetTest.start();
      } else if (devTest.equals("startup")) {
         DevStartupTest.start();
      } else if (devTest.equals("restore")) {
         DevRestoreTest.start();
      }
   }

   private static AttachableHeldLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> heldLayer(Object renderer) {
      return new AttachableHeldLayer<>((RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>>)renderer);
   }
}
