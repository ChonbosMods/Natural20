package com.chonbosmods.modelfix;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import org.objectweb.asm.*;

/**
 * Side-channels the currently-mined block face out of the Hytale interaction system so our
 * main plugin can read it during {@code BreakBlockEvent}. The event itself does not carry
 * face info, and the face lives several layers deep inside {@code InteractionManager}:
 * brittle to traverse and hard to identify "which chain fired".
 *
 * <h2>Patch 1: {@code BreakBlockEvent} static stash field</h2>
 * Adds a {@code public static volatile int NAT20_BLOCK_FACE} field to
 * {@link com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent}. Defaults to 0
 * (= {@code BlockFace.None}, meaning "not set"). We pick this class as the holder because
 * it's the exact type that our handler already operates on, so reflection lookup is obvious.
 *
 * <h2>Patch 2: {@code BreakBlockInteraction.interactWithBlock} entry stash</h2>
 * At method entry, inject the equivalent of:
 * <pre>
 *   BreakBlockEvent.NAT20_BLOCK_FACE = context.getState().blockFace.getValue();
 * </pre>
 * Context is local var 4 (method is instance, so slot 0 = this, slots 1-4 = first four params;
 * the decompile signature is {@code (World, CommandBuffer, InteractionType, InteractionContext, ...)}).
 *
 * <p>Because each world tick runs on the {@code TickingThread} serially, successive breaks on
 * that thread never race: the face gets overwritten before the next {@code BreakBlockEvent} fires.
 * If Hytale ever parallelizes world ticks this becomes unsafe and we'd move to a ThreadLocal.
 */
public class BlockFaceFix implements ClassTransformer {

    private static final String BREAK_EVENT_CLASS =
        "com/hypixel/hytale/server/core/event/events/ecs/BreakBlockEvent";
    private static final String BREAK_INTERACTION_CLASS =
        "com/hypixel/hytale/server/core/modules/interaction/interaction/config/client/BreakBlockInteraction";
    private static final String INTERACTION_CONTEXT =
        "com/hypixel/hytale/server/core/entity/InteractionContext";
    private static final String INTERACTION_SYNC_DATA =
        "com/hypixel/hytale/protocol/InteractionSyncData";
    private static final String BLOCK_FACE =
        "com/hypixel/hytale/protocol/BlockFace";

    public static final String FIELD_NAME = "NAT20_BLOCK_FACE";

    @Override
    public int priority() {
        return 1000;
    }

    @Override
    public byte[] transform(String className, String internalName, byte[] classBytes) {
        if (BREAK_EVENT_CLASS.equals(internalName)) {
            return addStashField(classBytes);
        }
        if (BREAK_INTERACTION_CLASS.equals(internalName)) {
            return injectFaceStash(classBytes);
        }
        return null;
    }

    /**
     * Patch 1: add a public static volatile int field to BreakBlockEvent. Default value is 0,
     * which corresponds to BlockFace.None (ordinal 0 per the enum definition).
     */
    private byte[] addStashField(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visitEnd() {
                FieldVisitor fv = cw.visitField(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE,
                    FIELD_NAME, "I", null, null);
                if (fv != null) fv.visitEnd();
                super.visitEnd();
                System.out.println("[BlockFaceFix] added static field " + BREAK_EVENT_CLASS + "." + FIELD_NAME);
            }
        }, 0);
        return cw.toByteArray();
    }

    /**
     * Patch 2: at the top of BreakBlockInteraction.interactWithBlock, inject:
     *   BreakBlockEvent.NAT20_BLOCK_FACE = context.getState().blockFace.getValue();
     *
     * Local layout (instance method, signature starts with World, CommandBuffer, InteractionType, InteractionContext):
     *   0 = this, 1 = world, 2 = commandBuffer, 3 = interactionType, 4 = interactionContext
     */
    private byte[] injectFaceStash(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"interactWithBlock".equals(name)) return mv;
                // Guard against accidentally matching a different override with a different arg layout.
                if (!descriptor.startsWith("(Lcom/hypixel/hytale/server/core/universe/world/World;"
                        + "Lcom/hypixel/hytale/component/CommandBuffer;"
                        + "Lcom/hypixel/hytale/protocol/InteractionType;"
                        + "L" + INTERACTION_CONTEXT + ";")) {
                    return mv;
                }

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    private boolean injected = false;

                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (injected) return;
                        injected = true;

                        // context.getState() -> InteractionSyncData
                        mv.visitVarInsn(Opcodes.ALOAD, 4);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, INTERACTION_CONTEXT, "getState",
                                "()L" + INTERACTION_SYNC_DATA + ";", false);
                        // .blockFace -> BlockFace
                        mv.visitFieldInsn(Opcodes.GETFIELD, INTERACTION_SYNC_DATA, "blockFace",
                                "L" + BLOCK_FACE + ";");
                        // .getValue() -> int
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BLOCK_FACE, "getValue", "()I", false);
                        // store into BreakBlockEvent.NAT20_BLOCK_FACE
                        mv.visitFieldInsn(Opcodes.PUTSTATIC, BREAK_EVENT_CLASS, FIELD_NAME, "I");

                        System.out.println("[BlockFaceFix] injected face stash at "
                                + BREAK_INTERACTION_CLASS + "#interactWithBlock");
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }
}
