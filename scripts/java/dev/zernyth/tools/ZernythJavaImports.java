package dev.zernyth.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

/**
 * Produces a read-only import-normalization plan using the installed JDK's resolved Java symbols.
 *
 * <p>Strings and comments are never searched/replaced. Ambiguous simple names remain qualified.
 * The caller must validate the explicit source list against the Zernyth ownership manifest
 * before applying the base64-encoded source plan. This tool does not modify files.
 */
public final class ZernythJavaImports {

    private ZernythJavaImports() {
    }

    /**
     * Analyzes the explicitly listed sources and emits path/tab/base64 records to standard output.
     *
     * @param arguments a UTF-8 source-list file, optionally followed by {@code --check-layout}
     * @throws Exception if source resolution fails; no partial plan is emitted
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 1 || arguments.length > 2
                || (arguments.length == 2 && !arguments[1].equals("--check-layout"))) {
            throw new IllegalArgumentException("Expected the reviewed source-list file");
        }
        List<Path> paths = Files.readAllLines(Path.of(arguments[0]), StandardCharsets.UTF_8)
                .stream().filter(line -> !line.isBlank()).map(Path::of).toList();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-encoding", "UTF-8", "-classpath",
                            System.getProperty("java.class.path")), null,
                    files.getJavaFileObjectsFromPaths(paths));
            var units = new ArrayList<CompilationUnitTree>();
            task.parse().forEach(units::add);
            task.analyze();
            if (diagnostics.getDiagnostics().stream()
                    .anyMatch(item -> item.getKind() == Diagnostic.Kind.ERROR)) {
                throw new IllegalStateException(diagnostics.getDiagnostics().toString());
            }
            Trees trees = Trees.instance(task);
            var plan = new ArrayList<String>();
            for (CompilationUnitTree unit : units) {
                Path path = Path.of(unit.getSourceFile().toUri());
                String source = Files.readString(path, StandardCharsets.UTF_8).replace("\ufeff", "");
                var normalizer = new Normalizer(unit, source, trees, task);
                String normalized = normalizer.normalize();
                if (arguments.length == 2) {
                    normalizer.checkLayout();
                }
                plan.add(path + "\t" + Base64.getEncoder().encodeToString(
                        normalized.getBytes(StandardCharsets.UTF_8)));
            }
            plan.forEach(System.out::println);
        }
    }

    private record Edit(int start, int end, String replacement) {

    }

    private static final class Normalizer extends TreePathScanner<Void, Void> {

        private final CompilationUnitTree unit;
        private final String source;
        private final Trees trees;
        private final JavacTask task;
        private final Map<String, Set<String>> meanings = new HashMap<>();
        private final Map<String, TypeElement> unqualified = new HashMap<>();
        private final Map<Tree, TypeElement> qualified = new HashMap<>();
        private final Set<String> declaredNames = new HashSet<>();
        private final List<Edit> edits = new ArrayList<>();

        Normalizer(CompilationUnitTree unit, String source, Trees trees, JavacTask task) {
            this.unit = unit;
            this.source = source;
            this.trees = trees;
            this.task = task;
        }

        @Override
        public Void visitImport(ImportTree declaration, Void ignored) {
            return null;
        }

        @Override
        public Void visitClass(ClassTree declaration, Void ignored) {
            declaredNames.add(declaration.getSimpleName().toString());
            return super.visitClass(declaration, ignored);
        }

        @Override
        public Void visitTypeParameter(TypeParameterTree declaration, Void ignored) {
            declaredNames.add(declaration.getName().toString());
            return super.visitTypeParameter(declaration, ignored);
        }

        @Override
        public Void visitVariable(VariableTree declaration, Void ignored) {
            declaredNames.add(declaration.getName().toString());
            return super.visitVariable(declaration, ignored);
        }

        @Override
        public Void visitIdentifier(IdentifierTree identifier, Void ignored) {
            Element element = trees.getElement(getCurrentPath());
            int start = start(identifier);
            int end = end(identifier);
            if (element instanceof TypeElement type && start >= 0 && end >= start
                    && source.substring(start, end).equals(identifier.getName().toString())) {
                remember(type);
                unqualified.put(type.getQualifiedName().toString(), type);
            }
            return super.visitIdentifier(identifier, ignored);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree selection, Void ignored) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof TypeElement type) {
                remember(type);
                int start = start(selection);
                int end = end(selection);
                if (start >= 0 && end >= start
                        && source.substring(start, end).equals(type.getQualifiedName().toString())) {
                    qualified.put(selection, type);
                    return null;
                }
            }
            return super.visitMemberSelect(selection, ignored);
        }

        private void remember(TypeElement type) {
            meanings.computeIfAbsent(type.getSimpleName().toString(), key -> new HashSet<>())
                    .add(type.getQualifiedName().toString());
        }

        private int start(Tree tree) {
            return (int) trees.getSourcePositions().getStartPosition(unit, tree);
        }

        private int end(Tree tree) {
            return (int) trees.getSourcePositions().getEndPosition(unit, tree);
        }

        private boolean needsImport(TypeElement type) {
            String ownPackage = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            String typePackage = task.getElements().getPackageOf(type).getQualifiedName().toString();
            boolean nestedType = type.getEnclosingElement().getKind().isClass()
                    || type.getEnclosingElement().getKind().isInterface();
            return nestedType || (!typePackage.equals(ownPackage) && !typePackage.equals("java.lang"));
        }

        String normalize() {
            scan(unit, null);
            var imports = new LinkedHashSet<String>();
            for (var item : unqualified.entrySet()) {
                if (needsImport(item.getValue()) && !declaredNames.contains(
                        item.getValue().getSimpleName().toString())) {
                    imports.add(item.getKey());
                }
            }
            for (var item : qualified.entrySet()) {
                TypeElement type = item.getValue();
                String simple = type.getSimpleName().toString();
                if (declaredNames.contains(simple) || meanings.get(simple).size() > 1) {
                    continue;
                }
                edits.add(new Edit(start(item.getKey()), end(item.getKey()), simple));
                if (needsImport(type)) {
                    imports.add(type.getQualifiedName().toString());
                }
            }
            StringBuilder block = new StringBuilder();
            for (ImportTree declaration : unit.getImports()) {
                if (declaration.isStatic()) {
                    block.append(declaration).append('\n');
                }
            }
            boolean platformGroup = true;
            List<String> sortedImports = imports.stream().sorted(Comparator.comparingInt((String name) ->
                            name.startsWith("java.") ? 0 : name.startsWith("javax.") ? 1 : 2)
                    .thenComparing(Comparator.naturalOrder())).toList();
            for (String name : sortedImports) {
                if (platformGroup && !name.startsWith("java.") && !name.startsWith("javax.")) {
                    if (!block.isEmpty()) {
                        block.append('\n');
                    }
                    platformGroup = false;
                }
                block.append("import ").append(name).append(";\n");
            }
            List<? extends ImportTree> originalImports = unit.getImports();
            if (originalImports.isEmpty()) {
                if (!block.isEmpty()) {
                    if (unit.getPackageName() == null) {
                        throw new IllegalArgumentException(
                                "Zernyth Java sources require a package declaration");
                    }
                    int packageEnd = source.indexOf(';', end(unit.getPackageName())) + 1;
                    edits.add(new Edit(packageEnd, packageEnd, "\n\n" + block));
                }
            } else {
                edits.add(new Edit(start(originalImports.getFirst()), end(originalImports.getLast()),
                        block.toString().stripTrailing()));
            }
            edits.sort(Comparator.comparingInt(Edit::start).reversed());
            StringBuilder result = new StringBuilder(source);
            for (Edit edit : edits) {
                result.replace(edit.start(), edit.end(), edit.replacement());
            }
            return result.toString();
        }

        void checkLayout() {
            new TreePathScanner<Void, Void>() {
                private void checkIndent(Tree declaration) {
                    int offset = start(declaration);
                    if (offset < 0 || end(declaration) < 0) {
                        return;
                    }
                    int lineStart = source.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
                    String indentation = source.substring(lineStart, offset);
                    if (indentation.isBlank() &&
                            (indentation.contains("\t") || indentation.length() % 4 != 0)) {
                        throw new IllegalStateException(unit.getSourceFile().getName() + ":"
                                + unit.getLineMap().getLineNumber(offset) +
                                ": declaration indentation is not four spaces");
                    }
                }

                @Override
                public Void visitClass(ClassTree declaration, Void ignored) {
                    checkIndent(declaration);
                    return super.visitClass(declaration, ignored);
                }

                @Override
                public Void visitMethod(MethodTree method, Void ignored) {
                    checkIndent(method);
                    if (method.getBody() != null && start(method.getBody()) >= 0 &&
                            end(method.getBody()) > 0) {
                        long firstLine = unit.getLineMap().getLineNumber(start(method.getBody()));
                        long lastLine = unit.getLineMap().getLineNumber(end(method.getBody()) - 1);
                        if (firstLine == lastLine) {
                            throw new IllegalStateException(unit.getSourceFile().getName() + ":" + firstLine
                                    + ": one-line method or constructor body");
                        }
                    }
                    return super.visitMethod(method, ignored);
                }
            }.scan(unit, null);
        }
    }
}
