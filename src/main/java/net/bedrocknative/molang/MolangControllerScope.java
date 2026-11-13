package net.bedrocknative.molang;

public interface MolangControllerScope {
   boolean allAnimationsFinished();

   boolean anyAnimationFinished();

   float stateTime();
}
