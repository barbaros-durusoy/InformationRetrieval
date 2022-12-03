package InformationRetrieval.Document;

import InformationRetrieval.Index.*;
import InformationRetrieval.Query.*;

import java.io.*;
import java.util.*;

public class MemoryCollection extends AbstractCollection{
    private final IndexType indexType;

    public MemoryCollection(String directory,
                            Parameter parameter){
        super(directory, parameter);
        this.indexType = parameter.getIndexType();
        if (parameter.loadIndexesFromFile()){
            loadIndexesFromFile(directory);
        } else {
            constructIndexesInMemory();
        }
        if (parameter.getDocumentType() == DocumentType.CATEGORICAL){
            positionalIndex.setCategoryCounts(documents);
            categoryTree.setRepresentativeCount(parameter.getRepresentativeCount());
        }
    }

    protected void loadIndexesFromFile(String directory){
        dictionary = new TermDictionary(comparator, directory);
        invertedIndex = new InvertedIndex(directory);
        if (parameter.constructPositionalIndex()){
            positionalIndex = new PositionalIndex(directory);
            positionalIndex.setDocumentSizes(documents);
        }
        if (parameter.constructPhraseIndex()){
            phraseDictionary = new TermDictionary(comparator, directory + "-phrase");
            phraseIndex = new InvertedIndex(directory + "-phrase");
            if (parameter.constructPositionalIndex()){
                phrasePositionalIndex = new PositionalIndex(directory + "-phrase");
            }
        }
        if (parameter.constructNGramIndex()){
            biGramDictionary = new TermDictionary(comparator, directory + "-biGram");
            triGramDictionary = new TermDictionary(comparator, directory + "-triGram");
            biGramIndex = new NGramIndex(directory + "-biGram");
            triGramIndex = new NGramIndex(directory + "-triGram");
        }
    }

    public void save(){
        if (indexType == IndexType.INVERTED_INDEX){
            dictionary.save(name);
            invertedIndex.save(name);
            if (parameter.constructPositionalIndex()){
                positionalIndex.save(name);
            }
            if (parameter.constructPhraseIndex()){
                phraseDictionary.save(name + "-phrase");
                phraseIndex.save(name + "-phrase");
                if (parameter.constructPositionalIndex()){
                    phrasePositionalIndex.save(name + "-phrase");
                }
            }
            if (parameter.constructNGramIndex()){
                biGramDictionary.save(name + "-biGram");
                triGramDictionary.save(name + "-triGram");
                biGramIndex.save(name + "-biGram");
                triGramIndex.save(name + "-triGram");
            }
        }
        if (parameter.getDocumentType() == DocumentType.CATEGORICAL){
            saveCategories();
        }
    }

    private void saveCategories(){
        try{
            PrintWriter printWriter = new PrintWriter(name + "-categories.txt", "UTF-8");
            for (Document document : documents){
                printWriter.write(document.getDocId() + "\t" + document.getCategory() + "\n");
            }
            printWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void constructIndexesInMemory(){
        ArrayList<TermOccurrence> terms = constructTerms(TermType.TOKEN);
        dictionary = new TermDictionary(comparator, terms);
        switch (indexType){
            case INCIDENCE_MATRIX:
                incidenceMatrix = new IncidenceMatrix(terms, dictionary, documents.size());
                break;
            case INVERTED_INDEX:
                invertedIndex = new InvertedIndex(dictionary, terms, comparator);
                if (parameter.constructPositionalIndex()){
                    positionalIndex = new PositionalIndex(dictionary, terms, comparator);
                }
                if (parameter.constructPhraseIndex()){
                    terms = constructTerms(TermType.PHRASE);
                    phraseDictionary = new TermDictionary(comparator, terms);
                    phraseIndex = new InvertedIndex(phraseDictionary, terms, comparator);
                    if (parameter.constructPositionalIndex()){
                        phrasePositionalIndex = new PositionalIndex(phraseDictionary, terms, comparator);
                    }
                }
                if (parameter.constructNGramIndex()){
                    constructNGramIndex();
                }
                if (parameter.getDocumentType() == DocumentType.CATEGORICAL){
                    categoryTree = new CategoryTree(name);
                    for (Document document : documents){
                        document.loadCategory(categoryTree);
                    }
                }
                break;
        }
    }
    private ArrayList<TermOccurrence> constructTerms(TermType termType){
        TermOccurrenceComparator termComparator = new TermOccurrenceComparator(comparator);
        ArrayList<TermOccurrence> terms = new ArrayList<>();
        ArrayList<TermOccurrence> docTerms;
        for (Document doc : documents){
            DocumentText documentText = doc.loadDocument();
            docTerms = documentText.constructTermList(doc.getDocId(), termType);
            terms.addAll(docTerms);
        }
        terms.sort(termComparator);
        return terms;
    }

    private QueryResult attributeSearch(Query query){
        Query termAttributes = new Query();
        Query phraseAttributes = new Query();
        QueryResult termResult = new QueryResult(), phraseResult = new QueryResult();
        query.filterAttributes(attributeList, termAttributes, phraseAttributes);
        if (termAttributes.size() > 0){
            termResult = invertedIndex.search(termAttributes, dictionary);
        }
        if (phraseAttributes.size() > 0){
            phraseResult = phraseIndex.search(phraseAttributes, phraseDictionary);
        }
        if (termAttributes.size() == 0){
            return phraseResult;
        }
        if (phraseAttributes.size() == 0){
            return termResult;
        }
        return termResult.intersection(phraseResult);
    }

    private QueryResult searchWithInvertedIndex(Query query, SearchParameter parameter){
        switch (parameter.getRetrievalType()){
            case    BOOLEAN:
                return invertedIndex.search(query, dictionary);
            case POSITIONAL:
                return positionalIndex.positionalSearch(query, dictionary);
            case RANKED:
                return positionalIndex.rankedSearch(query,
                        dictionary,
                        documents,
                        parameter.getTermWeighting(),
                        parameter.getDocumentWeighting(),
                        parameter.getDocumentsRetrieved());
            case ATTRIBUTE:
                return attributeSearch(query);
        }
        return new QueryResult();
    }

    private QueryResult filterAccordingToCategories(QueryResult currentResult, ArrayList<CategoryNode> categories){
        QueryResult filteredResult = new QueryResult();
        ArrayList<QueryResultItem> items = currentResult.getItems();
        for (QueryResultItem queryResultItem : items) {
            CategoryNode categoryNode = documents.get(queryResultItem.getDocId()).getCategoryNode();
            for (CategoryNode possibleAncestor : categories){
                if (categoryNode.isDescendant(possibleAncestor)) {
                    filteredResult.add(queryResultItem.getDocId(), queryResultItem.getScore());
                    break;
                }
            }
        }
        return filteredResult;
    }

    public QueryResult searchCollection(Query query, SearchParameter searchParameter){
        if (searchParameter.getFocusType().equals(FocusType.CATEGORY)){
            QueryResult currentResult = searchWithInvertedIndex(query, searchParameter);
            ArrayList<CategoryNode> categories = categoryTree.getCategories(query, dictionary, searchParameter.getCategoryDeterminationType());
            return filterAccordingToCategories(currentResult, categories);
        } else {
            switch (indexType){
                case INCIDENCE_MATRIX:
                    return incidenceMatrix.search(query, dictionary);
                case   INVERTED_INDEX:
                    return searchWithInvertedIndex(query, searchParameter);
            }
        }
        return new QueryResult();
    }

}
