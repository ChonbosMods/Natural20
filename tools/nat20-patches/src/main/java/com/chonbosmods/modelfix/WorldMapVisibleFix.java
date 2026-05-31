package com.chonbosmods.modelfix;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import org.objectweb.asm.*;

/**
 * Re-exposes the per-player "is the world map currently open" signal that Update 5 removed.
 *
 * <p>Through Update 4 the server tracked this as {@code WorldMapTracker.clientHasWorldMapVisible},
 * and our quest-marker provider read it via reflection to decide whether to emit the map-only
 * area-ring markers. U5 deleted that field and no longer stores the state anywhere queryable: the
 * {@code UpdateWorldMapVisible} packet is handled transiently in
 * {@code GamePacketHandler#handleUpdateWorldMapVisible} and discarded. Without it, the ring
 * markers can no longer be gated on map-open and leak onto the compass.
 *
 * <h2>Patch 1: {@code WorldMapTracker} stash field</h2>
 * Adds a {@code public boolean nat20MapVisible} instance field. One per player (each player owns a
 * {@code WorldMapTracker}), default {@code false} (= map closed). The main plugin reads it via
 * reflection off {@code player.getWorldMapTracker()}.
 *
 * <h2>Patch 2: {@code GamePacketHandler.handleUpdateWorldMapVisible} entry stash</h2>
 * At method entry, inject the equivalent of:
 * <pre>
 *   Player p = (Player) store.getComponent(ref, Player.getComponentType());
 *   if (p != null) p.getWorldMapTracker().nat20MapVisible = packet.visible;
 * </pre>
 * This mirrors exactly how the handler's own body resolves the player a few instructions later.
 *
 * <p>Local layout (instance method
 * {@code handleUpdateWorldMapVisible(UpdateWorldMapVisible, PlayerRef, Ref, World, Store)}):
 * 0 = this, 1 = packet, 2 = playerRef, 3 = ref, 4 = world, 5 = store.
 */
public class WorldMapVisibleFix implements ClassTransformer {

    private static final String TRACKER_CLASS =
        "com/hypixel/hytale/server/core/universe/world/WorldMapTracker";
    private static final String HANDLER_CLASS =
        "com/hypixel/hytale/server/core/io/handlers/game/GamePacketHandler";
    private static final String PLAYER_CLASS =
        "com/hypixel/hytale/server/core/entity/entities/Player";
    private static final String STORE_CLASS =
        "com/hypixel/hytale/component/Store";
    private static final String COMPONENT_TYPE_CLASS =
        "com/hypixel/hytale/component/ComponentType";
    private static final String COMPONENT_CLASS =
        "com/hypixel/hytale/component/Component";
    private static final String PACKET_CLASS =
        "com/hypixel/hytale/protocol/packets/worldmap/UpdateWorldMapVisible";

    public static final String FIELD_NAME = "nat20MapVisible";

    @Override
    public int priority() {
        return 1000;
    }

    @Override
    public byte[] transform(String className, String internalName, byte[] classBytes) {
        if (TRACKER_CLASS.equals(internalName)) {
            return addVisibleField(classBytes);
        }
        if (HANDLER_CLASS.equals(internalName)) {
            return injectVisibleStash(classBytes);
        }
        return null;
    }

    /** Patch 1: add a {@code public boolean nat20MapVisible} instance field (default false). */
    private byte[] addVisibleField(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visitEnd() {
                FieldVisitor fv = cw.visitField(
                    Opcodes.ACC_PUBLIC, FIELD_NAME, "Z", null, null);
                if (fv != null) fv.visitEnd();
                super.visitEnd();
                System.out.println("[WorldMapVisibleFix] added field " + TRACKER_CLASS + "." + FIELD_NAME);
            }
        }, 0);
        return cw.toByteArray();
    }

    /**
     * Patch 2: at the top of handleUpdateWorldMapVisible, stash packet.visible onto the player's
     * WorldMapTracker. Null-guards the player lookup so a missing component never NPEs the handler.
     */
    private byte[] injectVisibleStash(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"handleUpdateWorldMapVisible".equals(name)) return mv;
                // Guard against signature drift: first param must be the packet type.
                if (!descriptor.startsWith("(L" + PACKET_CLASS + ";")) return mv;

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    private boolean injected = false;

                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (injected) return;
                        injected = true;

                        Label skip = new Label();
                        Label end = new Label();

                        // Player p = (Player) store.getComponent(ref, Player.getComponentType());
                        mv.visitVarInsn(Opcodes.ALOAD, 5);   // store
                        mv.visitVarInsn(Opcodes.ALOAD, 3);   // ref
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, PLAYER_CLASS, "getComponentType",
                                "()L" + COMPONENT_TYPE_CLASS + ";", false);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STORE_CLASS, "getComponent",
                                "(Lcom/hypixel/hytale/component/Ref;L" + COMPONENT_TYPE_CLASS + ";)L"
                                        + COMPONENT_CLASS + ";", false);
                        mv.visitTypeInsn(Opcodes.CHECKCAST, PLAYER_CLASS);

                        // if (p == null) skip
                        mv.visitInsn(Opcodes.DUP);
                        mv.visitJumpInsn(Opcodes.IFNULL, skip);

                        // p.getWorldMapTracker().nat20MapVisible = packet.visible;
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PLAYER_CLASS, "getWorldMapTracker",
                                "()L" + TRACKER_CLASS + ";", false);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);   // packet
                        mv.visitFieldInsn(Opcodes.GETFIELD, PACKET_CLASS, "visible", "Z");
                        mv.visitFieldInsn(Opcodes.PUTFIELD, TRACKER_CLASS, FIELD_NAME, "Z");
                        mv.visitJumpInsn(Opcodes.GOTO, end);

                        // skip: pop the dup'd null
                        mv.visitLabel(skip);
                        mv.visitInsn(Opcodes.POP);
                        mv.visitLabel(end);

                        System.out.println("[WorldMapVisibleFix] injected map-visible stash at "
                                + HANDLER_CLASS + "#handleUpdateWorldMapVisible");
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }
}
