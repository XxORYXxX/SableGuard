package org.spongepowered.asm.mixin.injection; public @interface At { String value(); String target() default ""; Shift shift() default Shift.NONE; enum Shift { NONE, BEFORE, AFTER } }
