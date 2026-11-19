package com.dafeiyu.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 记忆图谱的图结构。
 *
 * 节点 = 一条记忆；边 = 内容相似度高的一对。
 * 相似度用 bigram 戴斯系数，够快也够准。
 */
public final class MemGraph {

    /** 最多画多少个点。再多 O(n^2) 会把界面卡死。 */
    public static final int MAX_NODES = 150;
    /** 边阈值。低于这个不连线，免得糊成一团。 */
    public static final float LINK = 0.34f;
    /** 每个点最多连几条边。 */
    public static final int MAX_EDGES_PER_NODE = 4;

    public static final class Node {
        public String title;
        public String body;
        public String track;      // 显示用的分类名
        public String realTrack;  // 真实轨道（user/memory/project/key/daily）
        public String at;         // 时间戳，用来精确定位
        public double weight;
        // 布局用
        public float x, y, vx, vy, r;
        public int index;
    }

    public static final class Edge {
        public int a, b;
        public float sim;
    }

    public final List<Node> nodes = new ArrayList<Node>();
    public final List<Edge> edges = new ArrayList<Edge>();

    /** 从记忆条目建图。 */
    public void setMemories(List<Node> input) {
        nodes.clear();
        edges.clear();
        if (input == null) {
            return;
        }
        List<Node> tmp = new ArrayList<Node>(input);
        // 只留权重最高的 MAX_NODES 个
        if (tmp.size() > MAX_NODES) {
            Collections.sort(tmp, new Comparator<Node>() {
                @Override
                public int compare(Node a, Node b) {
                    return Double.compare(b.weight, a.weight);
                }
            });
            while (tmp.size() > MAX_NODES) {
                tmp.remove(tmp.size() - 1);
            }
        }
        for (int i = 0; i < tmp.size(); i++) {
            Node n = tmp.get(i);
            n.index = i;
            nodes.add(n);
        }
        buildEdges();
    }

    private void buildEdges() {
        int n = nodes.size();
        if (n < 2) {
            return;
        }
        Set<Long> seen = new HashSet<Long>();
        int[] deg = new int[n];

        List<Edge> cand = new ArrayList<Edge>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                float s = sim(nodes.get(i).title, nodes.get(j).title);
                if (s >= LINK) {
                    Edge e = new Edge();
                    e.a = i;
                    e.b = j;
                    e.sim = s;
                    cand.add(e);
                }
            }
        }
        Collections.sort(cand, new Comparator<Edge>() {
            @Override
            public int compare(Edge x, Edge y) {
                return Float.compare(y.sim, x.sim);
            }
        });
        for (Edge e : cand) {
            if (deg[e.a] >= MAX_EDGES_PER_NODE || deg[e.b] >= MAX_EDGES_PER_NODE) {
                continue;
            }
            long key = ((long) Math.min(e.a, e.b) << 32) | Math.max(e.a, e.b);
            if (!seen.add(key)) {
                continue;
            }
            edges.add(e);
            deg[e.a]++;
            deg[e.b]++;
        }
    }

    /** bigram 戴斯系数。 */
    public static float sim(String a, String b) {
        if (a == null || b == null) {
            return 0f;
        }
        String x = norm(a);
        String y = norm(b);
        if (x.length() < 2 || y.length() < 2) {
            return x.equals(y) && !x.isEmpty() ? 1f : 0f;
        }
        Set<String> ga = grams(x);
        Set<String> gb = grams(y);
        if (ga.isEmpty() || gb.isEmpty()) {
            return 0f;
        }
        int inter = 0;
        for (String g : ga) {
            if (gb.contains(g)) {
                inter++;
            }
        }
        return (2f * inter) / (ga.size() + gb.size());
    }

    private static String norm(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static Set<String> grams(String s) {
        Set<String> out = new HashSet<String>();
        for (int i = 0; i + 1 < s.length(); i++) {
            out.add(s.substring(i, i + 2));
        }
        return out;
    }
}
