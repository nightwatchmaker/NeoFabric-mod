package org.neofabric.core;

import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Applies parsed Fabric class-tweaker rules to classfile bytes. */
public final class FabricClassTweakerTransformer implements BytecodeTransformPipeline.Transformer {
    private final List<FabricClassTweakerRule> rules;

    public FabricClassTweakerTransformer(List<FabricClassTweakerRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public byte[] transform(String className, byte[] bytecode) {
        String owner = className.replace('.', '/');
        boolean affectsClass = rules.stream().anyMatch(rule -> rule.owner().equals(owner) && rule.kind().equals("class"));
        boolean affectsMember = rules.stream().anyMatch(rule -> rule.owner().equals(owner) && !rule.kind().equals("class"));
        if (!affectsClass && !affectsMember) return bytecode;
        ClassReader reader = new ClassReader(bytecode);
        ClassWriter writer = new ClassWriter(reader, 0);
        final String targetOwner = owner;
        final List<FabricClassTweakerRule> classRules = rulesFor("class", targetOwner, "", "");
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, adjust(access, classRules), name, signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return super.visitField(adjust(access, rulesFor("field", targetOwner, name, descriptor)), name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return super.visitMethod(adjust(access, rulesFor("method", targetOwner, name, descriptor)), name, descriptor, signature, exceptions);
            }
        }, 0);
        return writer.toByteArray();
    }

    private List<FabricClassTweakerRule> rulesFor(String kind, String owner, String name, String descriptor) {
        return rules.stream().filter(rule -> rule.kind().equals(kind) && rule.owner().equals(owner)
                && rule.name().equals(name) && rule.descriptor().equals(descriptor)).toList();
    }

    private static int adjust(int access, List<FabricClassTweakerRule> matching) {
        int result = access;
        for (FabricClassTweakerRule rule : matching) {
            if (rule.action().equals("accessible") || rule.action().equals("extendable")) {
                result = (result & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
            }
            if (rule.action().equals("mutable") || rule.action().equals("extendable")) result &= ~Opcodes.ACC_FINAL;
        }
        return result;
    }
}
