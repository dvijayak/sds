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
 * A Sphinx4 version of the popular Jeopardy game. This applications is a Spoken 
 * Dialogue System and uses the Sphinx-4 endpointer, which automatically 
 * segments incoming audio into utterances and silences.
 */
public class SDS {

	private Recognizer recognizer;
	private JSGFGrammar grammarManager;		
	
	private Map<String, List<Question>> bank;
	private int score = 0;
	
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
            sds.exitApplication(1);
        }

        System.out.println("Welcome to Sphinx4 Jeopardy!");
        System.out.println("============================\n");        
        sds.showInstructions();
                
        sds.loadGameBoard();
                                        
        String[] categories = sds.bank.keySet().toArray(new String[sds.bank.size()]);
        int nCategories = categories.length;
                
        while (true) {
        	if (sds.bank.isEmpty()) {
        		System.out.println("Game Over! New Game?");
        		System.out.println("======================");
        		sds.showInstructions();
        	}
        	
            // Start speaking and recognize input speech
        	sds.loadGrammar("gameboard");        	
            Result result = sds.recognizer.recognize();

            if (result != null) {            	
                String resultText = result.getBestFinalResultNoFiller();                                
                if (resultText.length() > 0)
                {
                    // If user says Quit (or Bye), exit game
                    if (resultText.equalsIgnoreCase("quit")) {
                    	System.out.println("Bye now!");                	
                    	sds.exitApplication(1);
                    }                          
                    else if (resultText.equalsIgnoreCase("new game")) {
//                        System.out.println("You said: " + resultText + '\n');
                    	sds.startNewGame();
                    }
                    else if (resultText.equalsIgnoreCase("help"))
                    	sds.showInstructions();
                    else {                    	
                        // Parse the result text into meaningful tokens
                    	try {
        				    // Switch to the question bank grammar
        				    sds.loadGrammar("questionbank");
                    		
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
	    	            				    
	    	            				    // Display the question and retrieve/process the response
	    	            				    System.out.println("Question: " + questions[q].question);
	    	            				    Result answer = sds.recognizer.recognize();
	    	            				    if (answer != null) {
	    	            				    	String answerText = answer.getBestResultNoFiller();
	    	            				    	System.out.print("You answered: " + answerText + ".........");	    	            				    	
	    	            				    	if (answerText.equalsIgnoreCase(questions[q].answer)) {
	    	            				    		System.out.println("Correct! +" + questions[q].points + " :)\n");
	    	            				    		sds.score += questions[q].points;
	    	            				    	}
	    	            				    	else {
	    	            				    		System.out.println("Wrong! Sorry -" + questions[q].points + " :(\n");
	    	            				    		sds.score -= questions[q].points;
	    	            				    	}
	    	            				    	// Remove the question from the bank so the player can not try it again
	    	            				    	sds.bank.get(category).remove(q);
	    	            				    	if (sds.bank.get(category).isEmpty())
	    	            				    		sds.bank.remove(category);
	    	            				    } else 
	    	            		                System.out.println("I can't hear what you said.\n");	    	            		            	    	            				    
	    	            				    break;
	    	            			    }	    	            			   
	    	            		    }
	    	            		   
	    	            	    }
	    	                }
                    	} catch (NullPointerException e) {
                    		System.out.println("You can not choose that option again!\n");
                    	} catch (Exception e) {
                    		e.printStackTrace();
                    		sds.exitApplication(-1);
                    	}
                    	sds.drawGameBoard();
                    }
                }
                else
                	System.out.println("I can't hear what you said.\n");
            } else 
                System.out.println("I can't hear what you said.\n");
            
           
        }
    }   
    
    /**
     * Helper function for safely exiting the application under any circumstance
     * 
     * @param status
     * 
     * @author dvijayak
     */    
    private void exitApplication (int status) {
    	recognizer.deallocate();
    	removeGrammarFiles();
    	System.exit(status);
    }
    
    /**
     * Start a new game; reset score board; recreate question bank
     * 
     * @author dvijayak
     */
    private void startNewGame () {
    	System.out.println("======================");
    	System.out.println("Starting a new game...");
    	System.out.println("======================\n");
    	loadGameBoard();
    }
    
    /**
     * Load the grammar with the given grammar name. A previously loaded
     * grammar can be updated in this fashion.
     * 
     * @throws IOException if an I/O error occurs
     * @throws GrammarException if a grammar format error is detected
     * 
     * @author dvijayak
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
    }

    /**
     * Display game instructions/help to the player
     * 
     * @author dvijayak
     */    
    private void showInstructions () {
    	System.out.println("Instructions:\nChoose a command: [new game | help | quit] [<category> <points>]\n");
    }
    
    /**
     * Display the current status of the game (gameboard)
     * 
     * @author dvijayak
     */
    private void drawGameBoard () {
    	StringBuilder output = new StringBuilder();
    	output.append("Gameboard:\n");
        output.append("=========\n");
        String[] categories = bank.keySet().toArray(new String[bank.size()]);
        int nCategories = categories.length;
        for (int c = 0; c < nCategories; c++) {        	
        	output.append("Category: " + categories[c] + "\n");        	
        	output.append("Points:   ");        
        	Question[] questions = bank.get(categories[c]).toArray(new Question[bank.get(categories[c]).size()]);        
            int nQuestions = questions.length;
            for (int q = 0; q < nQuestions; q++) {
            	output.append(questions[q].points);        	
            	if (q != nQuestions - 1)
            		output.append(", ");
            }
            output.append("\n\n");
        }                      
        output.append("Score: " + score + "\n");
        output.append("=========\n");
        System.out.println(output.toString());
    }
    
    /**
     *  Initialize the game values
     *  
     *  @author dvijayak
     */
    private void loadGameBoard () {
    	System.out.println("Loading gameboard...");
    	try {    		 
//    		// Clear all grammar files from previous session
//    		removeGrammarFiles();
    		
    		// Initialize the score(s)
    		this.score = 0;
    		
    		/* Create the question bank and initialize the game resources */
    		
    		// Read the JSON file
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
				List<String> answers = new ArrayList<String>(); // List of question bank answers
				int nAnswers = 0;
				for (int c = 0; c < nCategories; c++) {
					JSONObject category = categories.getJSONObject(c);
					String name = category.getString("name");
					
					// Get all questions within this category
					List<Question> questionsList = new ArrayList<Question>();					
					JSONArray questions = category.getJSONArray("questions");					
					int nQuestions = questions.length();					
					for (int q = 0; q < nQuestions; q++, nAnswers++) {
						JSONObject question = questions.getJSONObject(q);
						String answer = question.getString("answer");
						answers.add(answer);
						questionsList.add(new Question(question.getString("question"), answer, question.getInt("points")));						
					}
					// Put the category into the game question bank
					this.bank.put(name, questionsList);
				}
				// Create the question bank grammar file
				createGrammarFile("Question Bank", answers.toArray(new String[nAnswers]));
				
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
       
    /**
     * Create a grammar file for the supplied question bank
     * 
     * @param grammarName
     * @param answers
     * 
     * @author dvijayak
     */
    private void createGrammarFile (String grammarName, String[] answers) {    	
		StringBuilder output = new StringBuilder();
		// Header
		output.append("#JSGF V1.0;\n\n/**\n * JSGF Grammar for Jeopardy: " + grammarName + "\n */\n\n");
		grammarName = grammarName.toLowerCase().replace(" ", "");
		output.append("grammar " + grammarName+ ";\n\n");
		// Body
		output.append("public <answer> = ");
		for (int a = 0; a < answers.length; a++) {
			output.append(answers[a]);
			if (a != answers.length - 1)
				output.append(" | ");
				
		}			
		output.append(";");
			
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File("src/sds/grammars/" + grammarName + ".gram")));
			bw.write(output.toString());
			bw.flush();
			bw.close();
		} catch (IOException e) { 
			e.printStackTrace();
		}
    }
    
    /**
     * [Re]Create a list of grammar files that are currently
     * in the question bank
     * 
     * TODO: This only works when creating per category; not the whole bank
     * 
     * @author dvijayak
     */
    private void createGrammarFiles () {
		// Get all categories
    	String[] categories = bank.keySet().toArray(new String[bank.size()]);				
		int nCategories = categories.length;			
		for (int c = 0; c < nCategories; c++) {    	    
	 	    // Get all questions for each category
		    Question[] questions = bank.get(categories[c]).toArray(new Question[bank.get(categories[c]).size()]);                		   		 
		    int nQuestions = questions.length;
		    // Store answers in an array
		    String[] answers = new String[nQuestions];
		    for (int q = 0; q < nQuestions; q++) {
		    	answers[q] = questions[q].answer;
		    }
		    // Create a grammar file for each category using the answers array
		    createGrammarFile(categories[c], answers);
        }
    }    
    
    /**
     * Remove all question grammar files
     * 
     * @author dvijayak
     */
    private void removeGrammarFiles () {
    	File folder = new File("src/sds/grammars/");
    	File[] files = folder.listFiles();
    	if (files != null) { // some JVMs return null for empty directories
    		for (File f: files) {
    			String fileName = f.getName();
    			// Delete all grammar files except for gameboard.gram [and sds.gram]
    			if (fileName.endsWith(".gram") && !fileName.equalsIgnoreCase("gameboard.gram") && !fileName.equalsIgnoreCase("sds.gram")) {
    				f.delete();
    			}    			
    		}
    	}
    		
    }
    
    /**
     * Inner class for the representation of a question
     * 
     * @author dvijayak     
     */    
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
