package com.chonbosmods.modelfix;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import org.objectweb.asm.*;

/**
 * Excludes Nat20-affixed ItemStacks from workbench recipe matching.
 *
 * <p>All Hytale crafting flows (ProcessingBenchState, Diagram, Structural, plus inventory-hint
 * scans) funnel through a single static method:
 * <pre>
 *   CraftingManager.matches(MaterialQuantity craftingMaterial, ItemStack itemStack) : boolean
 * </pre>
 * at {@code com/hypixel/hytale/builtin/crafting/component/CraftingManager}. The method compares
 * {@code craftingMaterial.getItemId()} (or its ResourceType fallback) against the stack's item
 * id. It makes no distinction between vanilla and metadata-bearing stacks, so our affixed
 * Iron Sword matches a recipe that asks for an Iron Sword and would be consumed.
 *
 * <p>Patch: at method entry, check if {@code itemStack.getMetadata()} contains the
 * {@code "Nat20Loot"} key. If it does, return {@code false} immediately so the affix-bearing
 * stack is considered a non-match for every recipe.
 *
 * <p>Uses inline bytecode referencing only {@code ItemStack}, {@code org.bson.BsonDocument},
 * and standard JDK types — all reachable from {@code TransformingClassLoader}. No
 * cross-classloader helper call needed.
 */
public class WorkbenchExcludeFix implements ClassTransformer {

    private static final String CRAFTING_MANAGER =
        "com/hypixel/hytale/builtin/crafting/component/CraftingManager";
    private static final String ITEM_STACK =
        "com/hypixel/hytale/server/core/inventory/ItemStack";
    private static final String BSON_DOCUMENT = "org/bson/BsonDocument";

    @Override
    public int priority() {
        return 1000;
    }

    @Override
    public byte[] transform(String className, String internalName, byte[] classBytes) {
        if (!CRAFTING_MANAGER.equals(internalName)) return null;

        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                // Match the static matches(MaterialQuantity, ItemStack): boolean method.
                boolean match = "matches".equals(name)
                        && descriptor.equals("(Lcom/hypixel/hytale/server/core/inventory/MaterialQuantity;"
                                             + "L" + ITEM_STACK + ";)Z");
                if (!match) return mv;

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    private boolean injected = false;

                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (injected) return;
                        injected = true;

                        // Local layout for this static method:
                        //   0 = craftingMaterial, 1 = itemStack
                        // Scratch local 2 used to hold the BsonDocument reference.
                        Label skip = new Label();

                        // BsonDocument meta = itemStack.getMetadata();
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ITEM_STACK, "getMetadata",
                                "()L" + BSON_DOCUMENT + ";", false);
                        mv.visitVarInsn(Opcodes.ASTORE, 2);

                        // if (meta == null) goto skip
                        mv.visitVarInsn(Opcodes.ALOAD, 2);
                        mv.visitJumpInsn(Opcodes.IFNULL, skip);

                        // if (!meta.containsKey("Nat20Loot")) goto skip
                        // BsonDocument extends Map<String,BsonValue>; containsKey is (Object)Z.
                        mv.visitVarInsn(Opcodes.ALOAD, 2);
                        mv.visitLdcInsn("Nat20Loot");
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BSON_DOCUMENT, "containsKey",
                                "(Ljava/lang/Object;)Z", false);
                        mv.visitJumpInsn(Opcodes.IFEQ, skip);

                        // return false;
                        mv.visitInsn(Opcodes.ICONST_0);
                        mv.visitInsn(Opcodes.IRETURN);

                        mv.visitLabel(skip);
                        System.out.println("[WorkbenchExcludeFix] injected Nat20Loot reject at "
                                + CRAFTING_MANAGER + "#matches");
                    }
                };
            }
        }, 0);

        return cw.toByteArray();
    }
}
