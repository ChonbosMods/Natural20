package com.chonbosmods.modelfix;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import org.objectweb.asm.*;

/**
 * Early plugin ClassTransformer that fixes Player model persistence in Update 4.
 *
 * Patch 1: In Model.createScaledModel(5-arg), replace "if (scale <= 0) throw"
 *          with "if (scale <= 0) scale = 1.0f". Safety net for any bad scale values.
 *
 * Patch 2: In Model$ModelReference.<clinit>, change the -1.0f constant in
 *          DEFAULT_PLAYER_MODEL construction to 1.0f.
 *
 * Patch 3: In Model.toReference(), remove the Player special case that returns
 *          DEFAULT_PLAYER_MODEL (which discards scale and attachments). Let Player
 *          models go through the normal path that preserves actual model data.
 */
public class PlayerModelScaleFix implements ClassTransformer {

    private static final String MODEL_CLASS = "com/hypixel/hytale/server/core/asset/type/model/config/Model";
    private static final String MODEL_REF_CLASS = MODEL_CLASS + "$ModelReference";

    @Override
    public int priority() {
        return 1000; // Run early
    }

    @Override
    public byte[] transform(String className, String internalName, byte[] classBytes) {
        if (MODEL_CLASS.equals(internalName)) {
            return patchModelClass(classBytes);
        }
        if (MODEL_REF_CLASS.equals(internalName)) {
            return patchDefaultPlayerModel(classBytes);
        }
        return null;
    }

    /**
     * Patch the Model class: both createScaledModel and toReference.
     */
    private byte[] patchModelClass(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                              String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                // Patch 1: createScaledModel scale safety
                if ("createScaledModel".equals(name)
                        && descriptor.startsWith("(Lcom/hypixel/hytale/server/core/asset/type/model/config/ModelAsset;FLjava/util/Map;Lcom/hypixel/hytale/math/shape/Box;Z)")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        private boolean injected = false;
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            if (!injected) {
                                Label skip = new Label();
                                mv.visitVarInsn(Opcodes.FLOAD, 1);
                                mv.visitInsn(Opcodes.FCONST_0);
                                mv.visitInsn(Opcodes.FCMPG);
                                mv.visitJumpInsn(Opcodes.IFGT, skip);
                                mv.visitInsn(Opcodes.FCONST_1);
                                mv.visitVarInsn(Opcodes.FSTORE, 1);
                                mv.visitLabel(skip);
                                injected = true;
                                System.out.println("[PlayerModelScaleFix] Patched Model.createScaledModel: scale <= 0 now defaults to 1.0f");
                            }
                        }
                    };
                }

                // Patch 3: toReference - skip the Player special case
                // Original bytecode:
                //   0: ldc "Player"
                //   3: aload_0
                //   4: getfield modelAssetId
                //   7: invokevirtual String.equals
                //  10: ifeq 17           <-- if NOT Player, go to normal path
                //  13: getstatic DEFAULT_PLAYER_MODEL
                //  16: areturn            <-- return stripped reference
                //  17: ...                <-- normal path preserving actual data
                //
                // We replace ifeq with goto so the Player check always falls through
                // to the normal path. This preserves scale and randomAttachmentIds.
                if ("toReference".equals(name)
                        && descriptor.equals("()Lcom/hypixel/hytale/server/core/asset/type/model/config/Model$ModelReference;")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        private boolean sawPlayerLdc = false;
                        private boolean patched = false;

                        @Override
                        public void visitLdcInsn(Object value) {
                            if (!patched && "Player".equals(value)) {
                                sawPlayerLdc = true;
                                // Don't emit the "Player" LDC: we're removing the entire check
                                return;
                            }
                            super.visitLdcInsn(value);
                        }

                        @Override
                        public void visitVarInsn(int opcode, int varIndex) {
                            if (sawPlayerLdc) {
                                // Skip: this is aload_0 for the equals check
                                return;
                            }
                            super.visitVarInsn(opcode, varIndex);
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String fname, String fdescriptor) {
                            if (sawPlayerLdc && opcode == Opcodes.GETFIELD) {
                                // Skip: this is getfield modelAssetId
                                return;
                            }
                            if (sawPlayerLdc && opcode == Opcodes.GETSTATIC) {
                                // Skip: this is getstatic DEFAULT_PLAYER_MODEL
                                return;
                            }
                            super.visitFieldInsn(opcode, owner, fname, fdescriptor);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mname,
                                                     String mdescriptor, boolean isInterface) {
                            if (sawPlayerLdc && "equals".equals(mname)) {
                                // Skip: this is String.equals
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, mname, mdescriptor, isInterface);
                        }

                        @Override
                        public void visitJumpInsn(int opcode, Label label) {
                            if (sawPlayerLdc && opcode == Opcodes.IFEQ) {
                                // Skip: this is ifeq (branch past the return)
                                // We've skipped everything up to here, so no branch needed
                                return;
                            }
                            super.visitJumpInsn(opcode, label);
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (sawPlayerLdc && opcode == Opcodes.ARETURN) {
                                // Skip: this is the early return of DEFAULT_PLAYER_MODEL
                                sawPlayerLdc = false;
                                patched = true;
                                System.out.println("[PlayerModelScaleFix] Patched Model.toReference: removed Player special case");
                                return;
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }

                return mv;
            }
        }, 0);

        return cw.toByteArray();
    }

    /**
     * Patch 2: In ModelReference's static initializer, change -1.0f to 1.0f
     * in DEFAULT_PLAYER_MODEL construction.
     */
    private byte[] patchDefaultPlayerModel(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, 0);

        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                              String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("<clinit>".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        private boolean sawPlayerString = false;

                        @Override
                        public void visitLdcInsn(Object value) {
                            if ("Player".equals(value)) {
                                sawPlayerString = true;
                                super.visitLdcInsn(value);
                                return;
                            }
                            if (sawPlayerString && value instanceof Float f && f == -1.0f) {
                                super.visitLdcInsn(1.0f);
                                sawPlayerString = false;
                                System.out.println("[PlayerModelScaleFix] Patched ModelReference.DEFAULT_PLAYER_MODEL: scale changed from -1.0f to 1.0f");
                                return;
                            }
                            sawPlayerString = false;
                            super.visitLdcInsn(value);
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            super.visitInsn(opcode);
                        }
                    };
                }
                return mv;
            }
        }, 0);

        return cw.toByteArray();
    }
}
