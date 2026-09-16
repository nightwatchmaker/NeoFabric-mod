package org.neofabric.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * Contributes Fabric class-tweaker rules to FML's native processor chain.
 *
 * This deliberately runs inside FML's ClassNode pipeline so access changes retain
 * FML's ordering, frame handling, audit trail, and Mixin integration.
 */
public final class NeoFabricClassProcessorProvider implements ClassProcessorProvider {
    @Override
    public void createProcessors(Context context, Collector collector) {
        Path mods;
        try {
            mods = net.neoforged.fml.loading.FMLLoader.getCurrent().getGameDir().resolve("mods");
        } catch (Throwable unavailable) {
            mods = Path.of(System.getProperty("neofabric.gameDir", ".")).resolve("mods");
        }
        if (!Files.isDirectory(mods)) return;
        List<FabricClassTweakerRule> rules = new ArrayList<>();
        try (var files = Files.list(mods)) {
            files.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
                try {
                    FabricModInfo info = FabricMetadataAdapter.read(path);
                    rules.addAll(FabricClassTweakerRule.parse(path, info));
                } catch (Exception ignored) {
                    // Non-Fabric jars and malformed optional metadata are handled by discovery.
                }
            });
        } catch (Exception ignored) {
            return;
        }
        if (!rules.isEmpty()) collector.add(new Processor(rules));
    }

    private static final class Processor implements ClassProcessor {
        private static final ProcessorName NAME = new ProcessorName("neofabric", "fabric_class_tweaker");
        private final List<FabricClassTweakerRule> rules;

        private Processor(List<FabricClassTweakerRule> rules) {
            this.rules = List.copyOf(rules);
        }

        @Override
        public ProcessorName name() {
            return NAME;
        }

        @Override
        public boolean handlesClass(SelectionContext context) {
            String owner = context.type().getInternalName();
            return rules.stream().anyMatch(rule -> rule.owner().equals(owner));
        }

        @Override
        public ComputeFlags processClass(TransformationContext context) {
            String owner = context.type().getInternalName();
            ClassNode node = context.node();
            node.access = adjust(node.access, rulesFor("class", owner, "", ""));
            node.fields.forEach(field -> field.access = adjust(field.access,
                    rulesFor("field", owner, field.name, field.desc)));
            node.methods.forEach(method -> method.access = adjust(method.access,
                    rulesFor("method", owner, method.name, method.desc)));
            context.audit("Applied Fabric class-tweaker rules", owner);
            return ComputeFlags.SIMPLE_REWRITE;
        }

        private List<FabricClassTweakerRule> rulesFor(String kind, String owner, String name, String descriptor) {
            return rules.stream().filter(rule -> rule.kind().equals(kind)
                    && rule.owner().equals(owner) && rule.name().equals(name)
                    && rule.descriptor().equals(descriptor)).toList();
        }

        private static int adjust(int access, List<FabricClassTweakerRule> matching) {
            int result = access;
            for (FabricClassTweakerRule rule : matching) {
                if (rule.action().equals("accessible") || rule.action().equals("extendable")) {
                    result = (result & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                }
                if (rule.action().equals("mutable") || rule.action().equals("extendable")) {
                    result &= ~Opcodes.ACC_FINAL;
                }
            }
            return result;
        }
    }
}
