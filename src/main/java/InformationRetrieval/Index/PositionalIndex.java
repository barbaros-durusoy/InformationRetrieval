package InformationRetrieval.Index;

import InformationRetrieval.Document.Document;
import InformationRetrieval.Document.DocumentWeighting;
import InformationRetrieval.Query.Query;
import InformationRetrieval.Query.QueryResult;
import InformationRetrieval.Query.VectorSpaceModel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class PositionalIndex {

    private final PositionalPostingList[] positionalIndex;

    private final int dictionarySize;

    public PositionalIndex(int dictionarySize){
        this.dictionarySize = dictionarySize;
        positionalIndex = new PositionalPostingList[dictionarySize];
        for (int i = 0; i < dictionarySize; i++){
            positionalIndex[i] = new PositionalPostingList();
        }
    }

    public PositionalIndex(String fileName, int dictionarySize){
        this.dictionarySize = dictionarySize;
        positionalIndex = new PositionalPostingList[dictionarySize];
        readPositionalPostingList(fileName);
    }

    private void readPositionalPostingList(String fileName){
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(fileName + "-positionalPostings.txt")), StandardCharsets.UTF_8));
            String line = br.readLine();
            int wordId = 0;
            while (line != null){
                String[] items = line.split(" ");
                if (wordId == Integer.parseInt(items[0])){
                    positionalIndex[wordId] = new PositionalPostingList();
                    for (int i = 0; i < Integer.parseInt(items[1]); i++){
                        line = br.readLine().trim();
                        String[] ids = line.split(" ");
                        int numberOfPositionalPostings = Integer.parseInt(ids[1]);
                        if (ids.length == numberOfPositionalPostings + 2){
                            int docId = Integer.parseInt(ids[0]);
                            for (int j = 0; j < numberOfPositionalPostings; j++){
                                int positionalPosting = Integer.parseInt(ids[j + 2]);
                                positionalIndex[wordId].add(docId, positionalPosting);
                            }
                        } else {
                            System.out.println("Mismatch in the number of postings for word " + wordId);
                        }
                    }
                } else {
                    System.out.println("Word Id's do not follow for word " + wordId);
                }
                wordId++;
                line = br.readLine();
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save(String fileName){
        try {
            PrintWriter printWriter = new PrintWriter(fileName + "-positionalPostings.txt", "UTF-8");
            for (int i = 0; i < dictionarySize; i++){
                if (positionalIndex[i].size() > 0){
                    printWriter.write(i + " " + positionalIndex[i].size() + "\n");
                    printWriter.write(positionalIndex[i].toString());
                }
            }
            printWriter.close();
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void addPosition(int termId, int docId, int position){
        positionalIndex[termId].add(docId, position);
    }

    public QueryResult positionalSearch(Query query, TermDictionary dictionary){
        int i, term;
        PositionalPostingList postingResult = null;
        for (i = 0; i < query.size(); i++){
            term = dictionary.getWordIndex(query.getTerm(i).getName());
            if (term != -1){
                if (i == 0){
                    postingResult = positionalIndex[term];
                } else {
                    if (postingResult != null){
                        postingResult = postingResult.intersection(positionalIndex[term]);
                    } else {
                        return null;
                    }
                }
            } else {
                return null;
            }
        }
        if (postingResult != null)
            return postingResult.toQueryResult();
        else
            return null;
    }

    public int[] getTermFrequencies(int docId){
        int[] tf;
        int index;
        PositionalPostingList positionalPostingList;
        tf = new int[dictionarySize];
        for (int i = 0; i < dictionarySize; i++){
            positionalPostingList = positionalIndex[i];
            index = positionalPostingList.getIndex(docId);
            if (index != -1){
                tf[i] = positionalPostingList.get(index).size();
            } else {
                tf[i] = 0;
            }
        }
        return tf;
    }

    public int[] getDocumentFrequencies(){
        int[] df;
        df = new int[dictionarySize];
        for (int i = 0; i < dictionarySize; i++){
            df[i] = positionalIndex[i].size();
        }
        return df;
    }

    public QueryResult rankedSearch(Query query, TermDictionary dictionary, ArrayList<Document> documents, TermWeighting termWeighting, DocumentWeighting documentWeighting){
        int i, j, term, docID, N = documents.size(), tf, df;
        QueryResult result = new QueryResult();
        double[] scores = new double[N];
        PositionalPostingList positionalPostingList;
        for (i = 0; i < query.size(); i++){
            term = dictionary.getWordIndex(query.getTerm(i).getName());
            if (term != -1){
                positionalPostingList = positionalIndex[term];
                for (j = 0; j < positionalPostingList.size(); j++){
                    PositionalPosting positionalPosting = positionalPostingList.get(j);
                    docID = positionalPosting.getDocId();
                    tf = positionalPosting.size();
                    df = positionalIndex[term].size();
                    if (tf > 0 && df > 0){
                        scores[docID] += VectorSpaceModel.weighting(tf, df, N, termWeighting, documentWeighting);
                    }
                }
            }
        }
        for (i = 0; i < N; i++){
            scores[i] /= documents.get(i).size();
            if (scores[i] > 0.0){
                result.add(i, scores[i]);
            }
        }
        result.sort();
        return result;
    }

}
