/*
 * Copyright 1999-2004 Carnegie Mellon University.
 * Portions Copyright 2004 Sun Microsystems, Inc.
 * Portions Copyright 2004 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 */

package sds;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.speech.recognition.GrammarException;
import javax.speech.recognition.RuleParse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.cmu.sphinx.frontend.util.Microphone;
import edu.cmu.sphinx.jsgf.JSGFGrammar;
import edu.cmu.sphinx.jsgf.JSGFGrammarException;
import edu.cmu.sphinx.jsgf.JSGFGrammarParseException;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.ConfigurationManager;


/**
 * A Spoken Dialogue System built using Sphinx-4. This application uses the Sphinx-4
 * endpointer, which automatically segments incoming audio into utterances and silences.
 */
public class SDS {

	private Recognizer recognizer;
	private JSGFGrammar grammarManager;		
	
	private Map<String, List<Question>> bank;	
	
	// Provides a mapping from full names of numbers to the numbers themselves
	private Map<String, Integer> pointsMap = fillPoints();	
	private Map<String, Integer> fillPoints () {
		Map<String, Integer> pointsMap = new HashMap<String, Integer>();
		pointsMap.put("ten", 10);
		pointsMap.put("twenty", 20);
		pointsMap.put("thirty-five", 35);
		pointsMap.put("thirtyfive", 35);
		pointsMap.put("thirty five", 35);
		pointsMap.put("sixty", 60);		
		return pointsMap; 
	}
	
    public static void main(String[] args) throws IOException, JSGFGrammarParseException, JSGFGrammarException, GrammarException {
        ConfigurationManager cm;

        if (args.length > 0) {
            cm = new ConfigurationManager(args[0]);
        } else {
            cm = new ConfigurationManager(SDS.class.getResource("sds.config.xml"));
        }
                
        SDS sds = new SDS();
        // Create and allocate the recognizer
        sds.recognizer = (Recognizer) cm.lookup("recognizer");
        sds.recognizer.allocate();
        
        // Get the JSGFGrammar component
    	sds.grammarManager = (JSGFGrammar) cm.lookup("jsgfGrammar");    	    	              

        // start the microphone or exit the program if this is not possible
        Microphone microphone = (Microphone) cm.lookup("microphone");
        if (!microphone.startRecording()) {
            System.out.println("Cannot start microphone.");
            sds.recognizer.deallocate();
            System.exit(1);
        }

        System.out.println("Welcome to Sphinx4 Jeopardy!");
        System.out.println("============================\n");        

        sds.loadGameBoard();
                
        int score = 0;
        String[] categories = sds.bank.keySet().toArray(new String[sds.bank.size()]);
        int nCategories = categories.length;
//        List<String> points = new ArrayList<String>();
        // loop the recognition until the user says "quit"
        while (true) {
            // Start speaking and recognize input speech
        	sds.loadGrammar("gameboard");
        	System.out.println("Choose a command: [new game | quit] [<category> <points>]");
            Result result = sds.recognizer.recognize();

            if (result != null) {            	
                String resultText = result.getBestFinalResultNoFiller();                                
                if (resultText.length() > 0)
                {
                    // If user says Quit, exit game
                    if (resultText.equalsIgnoreCase("quit")) {
                    	System.out.println("Bye now!");                	
                    	sds.recognizer.deallocate();
                    	System.exit(1);
                    }                          
                    else if (resultText.equalsIgnoreCase("new game")) {
                        System.out.println("You said: " + resultText + '\n');
                    }
                    else {
                        // Parse the result text into meaningful tokens
                    	try {
	                        String[] tokens = resultText.split(" ");
	                        String category = tokens[0], points = tokens[1];
	                         
	    	                for (int c = 0; c < nCategories; c++) {
	    	            	    if (category.equalsIgnoreCase(categories[c])) {         
	    	            	    	category = categories[c];
	    	            	 	    // Get the list of questions for the chosen category
	    	            		    Question[] questions = sds.bank.get(category).toArray(new Question[sds.bank.get(category).size()]);                		   
	    	            		   
	     	            		    // Find the specific question worth the chosen points value
	    	            		    int nQuestions = questions.length;
	    	            		    for (int q = 0; q < nQuestions; q++) {
	    	            			    if (questions[q].points == sds.pointsMap.get(points)) { // convert number full name to number value
	    	            				    System.out.println("You picked " + category + " for " + sds.pointsMap.get(points) + " points...\n");              
	    	            				    // Switch to the grammar of the question
	    	            				    sds.loadGrammar(category.toLowerCase() + "-" + "question" + q);
	    	            				    
	    	            				    System.out.println("Question: " + questions[q].question + "\n");
	    	            				    Result answer = sds.recognizer.recognize();
	    	            				    if (answer != null) {
	    	            				    	String answerText = answer.getBestResultNoFiller();
	    	            				    	System.out.println("You answered: " + answerText + "\n.........");	    	            				    	
	    	            				    	if (answerText.equalsIgnoreCase(questions[q].answer))
	    	            				    		System.out.println("Correct!");
	    	            				    } else {
	    	            		                System.out.println("I can't hear what you said.\n");
	    	            		            }	    	            				    
	    	            			    }                			   
	    	            			   
	    	            		    }
	    	            		   
	    	            		                   		 
	    	            		   
	    	            	    }
	    	                }
                    	} catch (Exception e) {
                    		e.printStackTrace();
                    	}
                    }
                }   
                
                sds.drawGameBoard();
            } else {
                System.out.println("I can't hear what you said.\n");
            }
           
        }
    }   
    
    /**
     * Load the grammar with the given grammar name. A previously loaded
     * grammar can be updated in this fashion.
     * 
     * @throws IOException if an I/O error occurs
     * @throws GrammarException if a grammar format error is detected
     */
    private void loadGrammar(String grammarName) throws GrammarException {
        try {
            grammarManager.loadJSGF(grammarName);
        } catch (JSGFGrammarException e) {
            throw new GrammarException (e.getMessage());
        } catch (JSGFGrammarParseException e) {
            throw new GrammarException (e.getMessage());
        } catch (IOException e) {
        	e.printStackTrace();
        }
        
//        dumpSampleSentences(grammarName);
    }



    /**
     * Dumps out a set of sample sentences for this grammar.  
     *  TODO: Note the current
     *  implementation just generates a large set of random utterances
     *  and tosses away any duplicates. There's no guarantee that this
     *  will generate all of the possible utterances. (yep, this is a hack)
     *
     */
    private void dumpSampleSentences(String title) {
        System.out.println(" ====== " + title + " ======");
        System.out.println("Possible commands: \n");
        grammarManager.dumpRandomSentences(200);
        System.out.println(" ============================");
    }
    
    private void drawGameBoard () {
    	StringBuilder output = new StringBuilder();
    	output.append("Gameboard:\n");
        output.append("=========\n");
        output.append("Categories: ");
        String[] categories = bank.keySet().toArray(new String[bank.size()]);
        int nCategories = categories.length;
        for (int c = 0; c < nCategories; c++) {
        	output.append(categories[c]);
        	if (c != nCategories - 1)
        		output.append(", ");
        }
        output.append("\n");
        output.append("Points:     ");
        Question[] questions = bank.get(categories[0]).toArray(new Question[bank.get(categories[0]).size()]);        
        int nQuestions = questions.length;
        for (int q = 0; q < nQuestions; q++) {
        	output.append(questions[q].points);        	
        	if (q != nQuestions - 1)
        		output.append(", ");
        }
        output.append("\n");
        output.append("=========\n");
        System.out.println(output.toString());
    }
    
    // Initialize gameboard values
    private void loadGameBoard () {
    	try {    		    	
			BufferedReader br = new BufferedReader(new FileReader(new File("src/sds/game.json")));
			
			StringBuilder output = new StringBuilder();
			String line = null;			
			while ((line = br.readLine()) != null) {
				output.append(line);
			}
			br.close();			
			String gameProperties = output.toString();
			
			// Populate the game question bank
			this.bank = new HashMap<String, List<Question>>();			
			try {								
				JSONObject jsonInit = new JSONObject(gameProperties);				
				JSONObject bank = jsonInit.getJSONObject("bank");
				
				// Get all categories
				JSONArray categories = bank.getJSONArray("categories");				
				int nCategories = categories.length();			
				for (int c = 0; c < nCategories; c++) {
					JSONObject category = categories.getJSONObject(c);
					String name = category.getString("name");
					
					// Get all questions within this category
					List<Question> questionsList = new ArrayList<Question>();					
					JSONArray questions = category.getJSONArray("questions");					
					int nQuestions = questions.length();				
					for (int q = 0; q < nQuestions; q++) {
						JSONObject question = questions.getJSONObject(q);
						String answer = question.getString("answer");
						questionsList.add(new Question(question.getString("question"), question.getString("answer"), question.getInt("points")));
						
						// Create question bank grammar file
						createQuestionGrammarFile(name + "-" + "Question" + (q + 1), answer);
					}
					// Put the category into the game question bank
					this.bank.put(name, questionsList);
				}								
				
			} catch (JSONException e) {
				e.printStackTrace();
			}		
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    	this.drawGameBoard();       
    }        
    
    private void createQuestionGrammarFile (String grammarName, String answer) {    	
		StringBuilder output = new StringBuilder();
		// Header
		output.append("#JSGF V1.0;\n\n/**\n * JSGF Grammar for Jeopardy: " + grammarName.replace("-", "/") + "\n */\n\n");
		output.append("grammar " + grammarName.toLowerCase().replace(" ", "") + ";\n\n");
		// Body
		output.append("public <answer> = " + answer + " {ANSWER};");
			
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File("src/sds/grammars/" + grammarName.toLowerCase() + ".gram")));
			bw.write(output.toString());
			bw.flush();
			bw.close();
		} catch (IOException e) { 
			e.printStackTrace();
		}
    }
    
    class Question {
    	String question;
    	String answer;
    	int points;
    	
    	public Question (String question, String answer, int points) {
    		this.question = question;
    		this.answer = answer;
    		this.points = points;
    	}
    }
}
