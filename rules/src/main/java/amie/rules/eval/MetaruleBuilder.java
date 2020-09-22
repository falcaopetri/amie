package amie.rules.eval;

import amie.data.KB;
import amie.data.Schema;
import amie.rules.AMIEParser;
import amie.rules.QueryEquivalenceChecker3;
import amie.rules.Rule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class implements a metarule strategy based on subsumption.
 * We group rules that are equivalent after transforming constants into variables,
 * Rules R, S are equivalent if length(R) == length(S) and R subsumes S.
 * @author falcaopetri
 */
public class MetaruleBuilder {

    public static void main(String args[]) throws IOException {
        if (args.length < 1) {
            System.err.println("MetaruleBuilder <inputfile>");
            System.exit(1);
        }

        File inputFile = new File(args[0]);

        Map<Rule, List<Rule>> metarules = groupAsMetarules(inputFile);
        for (Map.Entry<Rule, List<Rule>> e : metarules.entrySet()) {
            for (Rule rule : e.getValue()) {
                System.out.println(rule + "\t" + e.getKey());
            }
        }
    }

    private static int generifyTriple(int[] triple, int startCount) {
        int count = startCount;
        if (!KB.isVariable(triple[0])) {
            triple[0] = KB.map("?z" + count++);
        }

        if (!KB.isVariable(triple[2])) {
            triple[2] = KB.map("?z" + count++);
        }
        return count;
    }

    public static Rule toMetarule(Rule r) {
        // Get triples copy
        List<int[]> triples = r.getTriplesCopy();

        // Transform triples so constants become unique variables
        // We start by pos 1 (first body atom),
        // and lastly process pos 0 (the head atom).
        // This way, the new variables id grows from rule's left to right
        int count = 0;
        for (int i = 1; i < triples.size(); ++i) {
            int[] triple = triples.get(i);
            count = generifyTriple(triple, count);
        }
        generifyTriple(triples.get(0), count);

        // Build metarule
        Rule metarule = new Rule();
        metarule.getTriples().addAll(triples);
        return metarule;
    }

    public static Map<Rule, List<Rule>> groupAsMetarules(File inputFile) throws IOException {
        Map<Rule, List<Rule>> metarules = new HashMap<>();
        List<Rule> rules = AMIEParser.rules(inputFile);

        for (Rule q : rules) {
            Rule transformed = toMetarule(q);

            Rule metarule = metarules.keySet().stream()
                    .filter(transformed::equals)
                    .findFirst().orElse(transformed);

            // associate rule q with metarule
            List<Rule> values = metarules.computeIfAbsent(metarule, k -> new ArrayList<>());
            values.add(q);
        }
        return metarules;
    }
}
