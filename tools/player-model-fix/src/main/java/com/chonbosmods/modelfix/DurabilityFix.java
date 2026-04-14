package com.chonbosmods.modelfix;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import org.objectweb.asm.*;

/**
 * Early-plugin ClassTransformer for durability mechanics.
 *
 * <h2>Patch 1: Zero vanilla repair penalty</h2>
 * Transforms {@code RepairItemInteraction} so every GETFIELD of {@code repairPenalty}
 * returns {@code 0.0}. The formula
 * {@code newMax = oldMax - base * penalty * ratio} collapses to {@code newMax = oldMax}:
 * repairs no longer reduce max durability.
 *
 * <h2>Patch 2: Nat20 durability intercept (inline)</h2>
 * Transforms {@code LivingEntity.updateItemStackDurability} to inline a BSON-metadata check
 * at method entry. If the held tool's {@code Nat20Loot.AffixData} contains
 * {@code nat20:indestructible} the {@code durabilityChange} arg is zeroed; if it contains
 * {@code nat20:fortified} we zero on a 50% roll.
 *
 * <p>We inline the check rather than calling a static helper, because the early-plugin JAR
 * lives in a sibling classloader that the {@code TransformingClassLoader} can't resolve.
 * All types referenced here ({@code ItemStack}, {@code org.bson.*}, {@code String},
 * {@code ThreadLocalRandom}) resolve natively in the server classloader.
 */
public class DurabilityFix implements ClassTransformer {

    private static final String REPAIR_CLASS =
        "com/hypixel/hytale/server/core/entity/entities/player/pages/itemrepair/RepairItemInteraction";
    private static final String LIVING_ENTITY_CLASS =
        "com/hypixel/hytale/server/core/entity/LivingEntity";

    private static final String ITEM_STACK = "com/hypixel/hytale/server/core/inventory/ItemStack";
    private static final String BSON_DOCUMENT = "org/bson/BsonDocument";
    private static final String BSON_VALUE = "org/bson/BsonValue";
    private static final String BSON_STRING = "org/bson/BsonString";
    private static final String THREAD_LOCAL_RANDOM = "java/util/concurrent/ThreadLocalRandom";
    private static final String STRING = "java/lang/String";

    @Override
    public int priority() {
        return 1000;
    }

    @Override
    public byte[] transform(String className, String internalName, byte[] classBytes) {
        if (REPAIR_CLASS.equals(internalName)) {
            return patchRepairPenalty(classBytes);
        }
        if (LIVING_ENTITY_CLASS.equals(internalName)) {
            return patchDurabilityUpdate(classBytes);
        }
        return null;
    }

    /**
     * Patch 1: replace every GETFIELD of {@code repairPenalty} with {@code POP; DCONST_0}.
     * POP drops the preceding {@code this} ref (1 slot); DCONST_0 pushes 0.0 (2 slots) to
     * match the D-typed result of the original GETFIELD.
     */
    private byte[] patchRepairPenalty(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fname, String fdescriptor) {
                        if (opcode == Opcodes.GETFIELD
                                && "repairPenalty".equals(fname)
                                && REPAIR_CLASS.equals(owner)) {
                            mv.visitInsn(Opcodes.POP);
                            mv.visitInsn(Opcodes.DCONST_0);
                            System.out.println("[DurabilityFix] zeroed getfield repairPenalty in "
                                    + REPAIR_CLASS + "#" + name);
                            return;
                        }
                        super.visitFieldInsn(opcode, owner, fname, fdescriptor);
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }

    /**
     * Patch 2: inject the inline affix check at entry of updateItemStackDurability.
     *
     * Method signature:
     *   updateItemStackDurability(Ref, ItemStack, ItemContainer, int, double, ComponentAccessor)
     * Local layout:
     *   0=this, 1=ref, 2=itemStack, 3=container, 4=slotId, 5-6=durabilityChange, 7=componentAccessor
     * Scratch locals (added, 3 aref slots):
     *   8=temp Bson ref, 9=temp Bson ref, 10=affixStr
     */
    private byte[] patchDurabilityUpdate(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean match = "updateItemStackDurability".equals(name)
                        && descriptor.startsWith("(Lcom/hypixel/hytale/component/Ref;"
                                                  + "L" + ITEM_STACK + ";"
                                                  + "Lcom/hypixel/hytale/server/core/inventory/container/ItemContainer;ID"
                                                  + "Lcom/hypixel/hytale/component/ComponentAccessor;)");
                if (!match) return mv;

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    private boolean injected = false;

                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (injected) return;
                        injected = true;

                        Label done = new Label();
                        Label checkFortified = new Label();
                        Label zeroDurability = new Label();

                        // if (durabilityChange >= 0) goto done
                        mv.visitVarInsn(Opcodes.DLOAD, 5);
                        mv.visitInsn(Opcodes.DCONST_0);
                        mv.visitInsn(Opcodes.DCMPG);
                        mv.visitJumpInsn(Opcodes.IFGE, done);

                        // if (itemStack == null) goto done
                        mv.visitVarInsn(Opcodes.ALOAD, 2);
                        mv.visitJumpInsn(Opcodes.IFNULL, done);

                        // meta = itemStack.getMetadata()
                        mv.visitVarInsn(Opcodes.ALOAD, 2);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ITEM_STACK, "getMetadata",
                                "()L" + BSON_DOCUMENT + ";", false);
                        mv.visitInsn(Opcodes.DUP);
                        mv.visitVarInsn(Opcodes.ASTORE, 8);
                        mv.visitJumpInsn(Opcodes.IFNULL, done);

                        // lootVal = meta.get("Nat20Loot")
                        // BsonDocument.get is inherited from Map<String,BsonValue>, so the
                        // actual descriptor is (Object)BsonValue, not (String)BsonValue.
                        mv.visitVarInsn(Opcodes.ALOAD, 8);
                        mv.visitLdcInsn("Nat20Loot");
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSON_DOCUMENT, "get",
                                "(Ljava/lang/Object;)L" + BSON_VALUE + ";", false);
                        mv.visitInsn(Opcodes.DUP);
                        mv.visitVarInsn(Opcodes.ASTORE, 9);
                        mv.visitJumpInsn(Opcodes.IFNULL, done);

                        // if (!lootVal.isDocument()) goto done
                        mv.visitVarInsn(Opcodes.ALOAD, 9);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSON_VALUE, "isDocument", "()Z", false);
                        mv.visitJumpInsn(Opcodes.IFEQ, done);

                        // lootDoc = lootVal.asDocument() ; store in slot 8 (replacing meta)
                        mv.visitVarInsn(Opcodes.ALOAD, 9);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSON_VALUE, "asDocument",
                                "()L" + BSON_DOCUMENT + ";", false);
                        mv.visitVarInsn(Opcodes.ASTORE, 8);

                        // affixVal = lootDoc.get("AffixData")
                        mv.visitVarInsn(Opcodes.ALOAD, 8);
                        mv.visitLdcInsn("AffixData");
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSON_DOCUMENT, "get",
                                "(Ljava/lang/Object;)L" + BSON_VALUE + ";", false);
                        mv.visitInsn(Opcodes.DUP);
                        mv.visitVarInsn(Opcodes.ASTORE, 9);
                        mv.visitJumpInsn(Opcodes.IFNULL, done);

                        // if (!affixVal.isString()) goto done
                        mv.visitVarInsn(Opcodes.ALOAD, 9);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSON_VALUE, "isString", "()Z", false);
                        mv.visitJumpInsn(Opcodes.IFEQ, done);

                        // affixStr = affixVal.asString().getValue() ; store in slot 10
                        mv.visitVarInsn(Opcodes.ALOAD, 9);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSON_VALUE, "asString",
                                "()L" + BSON_STRING + ";", false);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSON_STRING, "getValue",
                                "()L" + STRING + ";", false);
                        mv.visitVarInsn(Opcodes.ASTORE, 10);

                        // if (affixStr.contains("nat20:indestructible")) goto zeroDurability
                        mv.visitVarInsn(Opcodes.ALOAD, 10);
                        mv.visitLdcInsn("nat20:indestructible");
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING, "contains",
                                "(Ljava/lang/CharSequence;)Z", false);
                        mv.visitJumpInsn(Opcodes.IFNE, zeroDurability);

                        // if (!affixStr.contains("nat20:fortified")) goto done
                        mv.visitVarInsn(Opcodes.ALOAD, 10);
                        mv.visitLdcInsn("nat20:fortified");
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING, "contains",
                                "(Ljava/lang/CharSequence;)Z", false);
                        mv.visitJumpInsn(Opcodes.IFEQ, done);

                        // if (ThreadLocalRandom.current().nextDouble() >= 0.5) goto done
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, THREAD_LOCAL_RANDOM, "current",
                                "()L" + THREAD_LOCAL_RANDOM + ";", false);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, THREAD_LOCAL_RANDOM, "nextDouble",
                                "()D", false);
                        mv.visitLdcInsn(0.5D);
                        // DCMPG: stack=(random, 0.5). 1 if random > 0.5, 0 if equal, -1 if less.
                        // We want to skip durability when random < 0.5, i.e., result == -1.
                        // IFLT jumps if result < 0.
                        mv.visitInsn(Opcodes.DCMPG);
                        mv.visitJumpInsn(Opcodes.IFGE, done);

                        // zeroDurability: durabilityChange = 0.0
                        mv.visitLabel(zeroDurability);
                        mv.visitInsn(Opcodes.DCONST_0);
                        mv.visitVarInsn(Opcodes.DSTORE, 5);

                        mv.visitLabel(done);
                        System.out.println("[DurabilityFix] injected inline Nat20 affix check at "
                                + LIVING_ENTITY_CLASS + "#updateItemStackDurability");
                    }
                };
            }
        }, 0);

        return cw.toByteArray();
    }
}
