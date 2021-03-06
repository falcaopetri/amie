package amie.rosa;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import amie.data.KB;
import amie.data.tuple.IntPair;
import amie.rules.AMIEParser;
import amie.rules.Rule;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;

public class EquivalenceRulesBuilder {

	private static void calculateMetrics(KB source, Rule candidate) {
		// TODO Auto-generated method stub
		List<int[]> antecedent = new ArrayList<int[]>();
		antecedent.addAll(candidate.getAntecedent());
		List<int[]> succedent = new ArrayList<int[]>();
		succedent.addAll(candidate.getTriples().subList(0, 1));
		long numerator = 0;
		long improvedDenominator = 0;
		long denominator = 0;
		int[] head = candidate.getHead();
		int[] existentialTriple = head.clone();
		int freeVarPos, countVarPos;
				
		countVarPos = source.functionality(head[1]) > source.inverseFunctionality(head[1]) ? 0 : 2;
		candidate.setFunctionalVariablePosition(countVarPos);
		if(KB.numVariables(existentialTriple) == 1){
			freeVarPos = KB.firstVariablePos(existentialTriple) == 0 ? 2 : 0;
		}else{
			freeVarPos = (existentialTriple[0] == candidate.getFunctionalVariable()) ? 2 : 0;
		}

		existentialTriple[freeVarPos] = KB.map("?x");
				
		//Confidence
		try{
			if(KB.numVariables(head) == 2){
				int var1, var2;
				var1 = head[KB.firstVariablePos(head)];
				var2 = head[KB.secondVariablePos(head)];
				
				numerator = source.countDistinctPairs(var1, var2, candidate.getTriples());
				denominator = source.countDistinctPairs(var1, var2, antecedent);
				candidate.setSupport((int)numerator);
				candidate.setBodySize((int)denominator);
				antecedent.add(existentialTriple);
				improvedDenominator = source.countDistinctPairs(var1, var2, antecedent);
				candidate.setPcaBodySize((int)improvedDenominator);
			}else if(KB.numVariables(head) == 1){
				int var = head[KB.firstVariablePos(head)];				
				numerator = source.countDistinct(var, candidate.getTriples());
				denominator = source.countDistinct(var, antecedent);
				antecedent.add(existentialTriple);
				improvedDenominator = source.countDistinct(var, antecedent);				
				candidate.setSupport((int)numerator);
				candidate.setBodySize((int)denominator);
				candidate.setPcaBodySize((int)improvedDenominator);
			}
		}catch(UnsupportedOperationException e){
			
		}
	}
	
	public static long calculateIntersection(KB source, Rule rule){
		int[] head = rule.getHead();
		if(KB.numVariables(head) == 2)
			return source.countDistinctPairs(head[0], head[2], rule.getTriples());
		else
			return source.countDistinct(head[KB.firstVariablePos(head)], rule.getTriples());
	}
	
	public static long calculateUnion(KB source, Rule rule){
		int[] head, body;
		head = rule.getHead();
		body = rule.getBody().get(0);
		if(KB.numVariables(head) == 2){
			Int2ObjectMap<IntSet> headBindings = source.selectDistinct(head[0], head[2], KB.triples(head));		
			Int2ObjectMap<IntSet> bodyBindings = source.selectDistinct(head[0], head[2], KB.triples(body));		
			Set<IntPair > pairs = new HashSet<IntPair>();
			
			for(int key1: headBindings.keySet()){
				for(int key2: headBindings.get(key1)){
					pairs.add(new IntPair(key1, key2));
				}
			}
			
			for(int key1: bodyBindings.keySet()){
				for(int key2: bodyBindings.get(key1)){
					pairs.add(new IntPair(key1, key2));
				}
			}		
			
			return pairs.size();
		}else{
			//Case for one variable
			IntSet headBindings = source.selectDistinct(head[KB.firstVariablePos(head)], KB.triples(head));
			headBindings.addAll(source.selectDistinct(head[KB.firstVariablePos(head)], KB.triples(body)));			
			return headBindings.size();
		}
	}
	
	public static List<ROSAEquivalence> findEquivalences(KB source, List<Rule> rules) {
		List<ROSAEquivalence> results = new ArrayList<>();
		boolean flags[] = new boolean[rules.size()];
		for(int i = 0; i < rules.size(); ++i)
			flags[i] = false;
		
		//Assume we get rules with 2 atoms
		//Look for patterns r(x,y) => r'(x,y) and r'(x,y) => r(x,y)
		for(int i = 0; i < rules.size(); ++i){
			if(flags[i]) continue;
			boolean twoVars = KB.numVariables(rules.get(i).getHead()) == 2;
			int r1, r2, r1p, r2p, t1, t2, t1p, t2p;
			r1 = rules.get(i).getHead()[1];
			r2 = rules.get(i).getBody().get(0)[1];
			t1 = rules.get(i).getHead()[2];
			t2 = rules.get(i).getBody().get(0)[2];
			for(int j = i + 1; j < rules.size(); ++j){
				boolean match = false;
				r1p = rules.get(j).getHead()[1];
				r2p = rules.get(j).getBody().get(0)[1];
				t1p = rules.get(j).getHead()[2];
				t2p = rules.get(j).getBody().get(0)[2];
				if(twoVars){
					match = (r1 == r2p) && (r2 == r1p);
				}else{
                                        match = (r1 == r2p) && (r2 == r1p) && (t1 == t2p) && (t2 == t1p);
				}
				if(match){
					flags[i] = true;
					flags[j] = true;
					long intersection, union;
					intersection = calculateIntersection(source, rules.get(i));
					union = calculateUnion(source, rules.get(i));
					results.add(new ROSAEquivalence(rules.get(i), rules.get(j), intersection, union));
					break;
				}
			}
		}
		
		return results;
	}
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		List<Rule> rules = AMIEParser.rules(new File(args[0]));
		KB source = new KB();
		boolean flags[] = new boolean[rules.size()];
		for(int i = 0; i < rules.size(); ++i)
			flags[i] = false;
		
		for(int i = 1; i < args.length; ++i)
			source.load(new File(args[i]));
		//Assume we get rules with 2 atoms
		//Look for patterns r(x,y) => r'(x,y) and r'(x,y) => r(x,y)
		for(int i = 0; i < rules.size(); ++i){
			if(flags[i]) continue;
			boolean twoVars = KB.numVariables(rules.get(i).getHead()) == 2;
			int r1, r2, r1p, r2p, t1, t2, t1p, t2p;
			r1 = rules.get(i).getHead()[1];
			r2 = rules.get(i).getBody().get(0)[1];
			t1 = rules.get(i).getHead()[2];
			t2 = rules.get(i).getBody().get(0)[2];
			for(int j = i + 1; j < rules.size(); ++j){
				boolean match = false;
				r1p = rules.get(j).getHead()[1];
				r2p = rules.get(j).getBody().get(0)[1];
				t1p = rules.get(j).getHead()[2];
				t2p = rules.get(j).getBody().get(0)[2];
				if(twoVars){
					match = (r1 == r2p) && (r2 == r1p);
				}else{
					match = (r1 == r2p) && (r2 == r1p) && (t1 == t2p) && (t2 == t1p);		
				}
				if(match){
					flags[i] = true;
					flags[j] = true;
					long intersection, union;
					calculateMetrics(source, rules.get(i));
					calculateMetrics(source, rules.get(j));
					intersection = calculateIntersection(source, rules.get(i));
					union = calculateUnion(source, rules.get(i));
					double pcaConf1 = (double)rules.get(i).getSupport() / (double)rules.get(i).getPcaBodySize();
					double pcaConf2 = (double)rules.get(j).getSupport() / (double)rules.get(j).getPcaBodySize();
					System.out.println(KB.toString(rules.get(i).getHead()) + " <=> " + KB.toString(rules.get(i).getBody().get(0)) + "\t" + intersection  + "\t" + union + "\t" + pcaConf1 + "\t" + pcaConf2);
					//System.out.println(KB.toString(rules.get(i).getHead()) + " <=> " + KB.toString(rules.get(i).getBody().get(0)));					
					break;
				}
			}
		}	
	}
}
