package net.bedrocknative.client.model;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.lang.reflect.Method;
import net.bedrocknative.BedrockNative;

final class ShaderPacks {
   static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
   static final String IRIS_PROGRAM = "net.irisshaders.iris.api.v0.IrisProgram";
   static final String IRIS_SHADOW_PROGRAM = "net.irisshaders.iris.api.v0.IrisShadowProgram";
   private static boolean warned;

   private ShaderPacks() {
   }

   static boolean assignTranslucentEntity(RenderPipeline pipeline) {
      return "off".equals(System.getProperty("bedrocknative.irisPipelines"))
         ? false
         : assign(
            pipeline,
            "net.irisshaders.iris.api.v0.IrisApi",
            "net.irisshaders.iris.api.v0.IrisProgram",
            "ENTITIES_TRANSLUCENT",
            "net.irisshaders.iris.api.v0.IrisShadowProgram",
            "SHADOW_ENTITIES"
         );
   }

   static boolean assign(RenderPipeline pipeline, String api, String program, String programName, String shadowProgram, String shadowName) {
      Class<?> apiClass;
      try {
         apiClass = Class.forName(api, true, ShaderPacks.class.getClassLoader());
      } catch (LinkageError | ClassNotFoundException var12) {
         return false;
      }

      try {
         Object instance = apiClass.getMethod("getInstance").invoke(null);
         Class<? extends Enum> programs = (Class<? extends Enum>)Class.forName(program, true, apiClass.getClassLoader());
         Method assign = apiClass.getMethod("assignPipeline", RenderPipeline.class, programs);
         assign.invoke(instance, pipeline, Enum.valueOf(programs, programName));
         Class<? extends Enum> shadows = (Class<? extends Enum>)Class.forName(shadowProgram, true, apiClass.getClassLoader());
         Method assignShadow = apiClass.getMethod("assignPipelineShadow", RenderPipeline.class, shadows);
         assignShadow.invoke(instance, pipeline, Enum.valueOf(shadows, shadowName));
         BedrockNative.LOGGER
            .info("Shader packs: {} drawn with Iris's {} program ({} in the shadow pass)", new Object[]{pipeline.getLocation(), programName, shadowName});
         return true;
      } catch (RuntimeException | LinkageError | ReflectiveOperationException var13) {
         if (!warned) {
            warned = true;
            BedrockNative.LOGGER
               .warn("Shader packs: Iris's pipeline API refused {} ({}); it draws unshaded under a shader pack", pipeline.getLocation(), var13.toString());
         }

         return false;
      }
   }
}
