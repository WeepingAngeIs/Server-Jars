package dev.unyielding.quests.quest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Tiny item filter language used by "smart filter" quest items, e.g.
 * <pre>or(item(cobblemon:oran_berry)item(cobblemon:sitrus_berry))</pre>
 * Supported: or(...), and(...), not(...), item(id), tag(id), mod(namespace).
 */
public final class ItemFilter {
    private static final ConcurrentHashMap<String, ItemFilter> CACHE = new ConcurrentHashMap<>();

    private final Predicate<ItemStack> predicate;
    private final List<Item> display;

    private ItemFilter(Predicate<ItemStack> p, List<Item> display) {
        this.predicate = p;
        this.display = display;
    }

    public static ItemFilter get(String text) {
        return CACHE.computeIfAbsent(text, ItemFilter::compile);
    }

    public boolean matches(ItemStack s) {
        return !s.isEmpty() && predicate.test(s);
    }

    /** Items worth showing as an icon cycle (explicit item(...) entries). */
    public List<Item> displayItems() {
        return display;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    // ---- parsing ---------------------------------------------------------

    private static ItemFilter compile(String text) {
        try {
            Parser p = new Parser(text);
            Node n = p.parseExpr();
            Set<Item> items = new LinkedHashSet<>();
            n.collect(items);
            return new ItemFilter(n::test, new ArrayList<>(items));
        } catch (RuntimeException e) {
            return new ItemFilter(s -> false, List.of());
        }
    }

    private interface Node {
        boolean test(ItemStack s);

        void collect(Set<Item> out);
    }

    private static final class Parser {
        final String t;
        int i;

        Parser(String t) {
            this.t = t;
        }

        void ws() {
            while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++;
        }

        Node parseExpr() {
            ws();
            int s = i;
            while (i < t.length() && (Character.isLetterOrDigit(t.charAt(i)) || t.charAt(i) == '_')) i++;
            String name = t.substring(s, i);
            ws();
            if (i >= t.length() || t.charAt(i) != '(') throw new IllegalArgumentException("expected (");
            i++;
            switch (name) {
                case "or", "and" -> {
                    List<Node> kids = new ArrayList<>();
                    while (true) {
                        ws();
                        if (i >= t.length()) throw new IllegalArgumentException("unterminated");
                        if (t.charAt(i) == ')') {
                            i++;
                            break;
                        }
                        kids.add(parseExpr());
                    }
                    boolean or = name.equals("or");
                    return new Node() {
                        public boolean test(ItemStack st) {
                            if (or) {
                                for (Node k : kids) if (k.test(st)) return true;
                                return false;
                            }
                            for (Node k : kids) if (!k.test(st)) return false;
                            return !kids.isEmpty();
                        }

                        public void collect(Set<Item> out) {
                            for (Node k : kids) k.collect(out);
                        }
                    };
                }
                case "not" -> {
                    Node k = parseExpr();
                    ws();
                    if (i < t.length() && t.charAt(i) == ')') i++;
                    return new Node() {
                        public boolean test(ItemStack st) {
                            return !k.test(st);
                        }

                        public void collect(Set<Item> out) {
                        }
                    };
                }
                default -> {
                    int e = t.indexOf(')', i);
                    if (e < 0) throw new IllegalArgumentException("unterminated");
                    String arg = t.substring(i, e).trim();
                    i = e + 1;
                    return leaf(name, arg);
                }
            }
        }

        Node leaf(String name, String arg) {
            switch (name) {
                case "item" -> {
                    ResourceLocation rl = ResourceLocation.tryParse(arg);
                    Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
                    return new Node() {
                        public boolean test(ItemStack st) {
                            return item != null && st.is(item);
                        }

                        public void collect(Set<Item> out) {
                            if (item != null) out.add(item);
                        }
                    };
                }
                case "tag" -> {
                    ResourceLocation rl = ResourceLocation.tryParse(arg.startsWith("#") ? arg.substring(1) : arg);
                    TagKey<Item> tag = rl == null ? null : TagKey.create(net.minecraft.core.registries.Registries.ITEM, rl);
                    return new Node() {
                        public boolean test(ItemStack st) {
                            return tag != null && st.is(tag);
                        }

                        public void collect(Set<Item> out) {
                            if (tag != null) {
                                BuiltInRegistries.ITEM.getTag(tag).ifPresent(h -> h.stream().limit(8).forEach(x -> out.add(x.value())));
                            }
                        }
                    };
                }
                case "mod" -> {
                    return new Node() {
                        public boolean test(ItemStack st) {
                            return BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace().equals(arg);
                        }

                        public void collect(Set<Item> out) {
                        }
                    };
                }
                default -> {
                    return new Node() {
                        public boolean test(ItemStack st) {
                            return false;
                        }

                        public void collect(Set<Item> out) {
                        }
                    };
                }
            }
        }
    }
}
