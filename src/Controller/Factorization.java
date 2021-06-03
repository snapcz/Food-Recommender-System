/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Controller;

import Interface.FactorizationData;
import Model.Dataset;
import Model.FactorMatrix;
import Model.FactorType;
import Util.FactorizationUtil;
import Util.MatrixUtil;
import Model.Recipe;
import Model.TestMatrix;
import Model.TrainMatrix;
import Model.User;
import Model.Pair;
import Model.RecipePredicted;
import com.opencsv.CSVWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 *
 * @author asus
 */
public class Factorization implements Runnable, FactorizationData {

    private final MainDocumentController mdc;
    private TrainMatrix trainM;
    private TestMatrix testM;
    private Dataset dataset;
    private FactorizationUtil utils;
    private List<Recipe> recipes;
    private List<User> users;
    private List<String> ingredients;
    private HashMap<String, Integer> userMap;     //memetakan ID user kepada index matrix user & list user
    private HashMap<String, Integer> recipeMap;   //memetakan ID resep kepada index matrix resep & list resep
    private HashMap<String, Integer> ingredientMap;
    private HashMap<Integer, String> userMap_r;   //memetakan index matrix / list user dengan ID user
    private HashMap<Integer, String> recipeMap_r; //memetakan index matriks / list resep dengan ID resep
    private HashMap<Integer, String> ingredientMap_r;
    private double[][] firstMethodRes;
    private double[][] secondMethodRes;
    private int MAX_LOOP;
    private int INIT_USER_SPACE; //used to check if there is already customize user rating
    private double MIN_OBJECTIVE_VAL = 0.0;
    private int learningType; //fixed or iteratively change to 1/t
    
    /*
    temporary attribute for automatic testing, delete later.
    */
    private double[] rmse_1;
    private double[] rmse_2;
    private long time1;
    private long time2;

    public Factorization(MainDocumentController c) {
        this.recipes = new ArrayList<Recipe>();
        this.users = new ArrayList<User>();
        this.ingredients = new ArrayList<String>();
        this.userMap = new HashMap  <String, Integer>();
        this.userMap_r = new HashMap<Integer, String>();
        this.recipeMap = new HashMap<String, Integer>();
        this.recipeMap_r = new HashMap<Integer, String>();
        this.ingredientMap = new HashMap<String, Integer>();
        this.ingredientMap_r = new HashMap<Integer, String>();
        this.firstMethodRes = null;
        this.secondMethodRes = null;
        this.mdc = c;
        
        /*
        temporary attribute for automatic testing, delete later.
        */
        rmse_1 = new double[3];
        rmse_2 = new double[3];
        time1 = 0L;
        time2 = 0L;
    }
    
    /*
        return new instance of factorization with the same reference on attribute:
            - List of Recipes
            - List of Users (deep copy)
            - List of Ingredients
            - MainDocummentController
            - Dataset (deep copy)
            - user index map (and reversed)
            - recipe index map (and reversed)
            - ingredient index map (and reversed)
        WARNING:
        This copy method do shallow copy on listed attribute, since these attributes will likely not gonna embrace any change
        while the program is running. This method also only valid to be used after the Factorization object
        has done method preprocessing (via isPreprocessed method)
    */
    protected Factorization copy(){
        Factorization newFactor = new Factorization(this.mdc);
        //shallow copy
        newFactor.setRecipes(recipes);
        newFactor.setIngredients(ingredients);
        newFactor.setUserMap(userMap);
        newFactor.setUserMapReversed(userMap_r);
        newFactor.setRecipeMap(recipeMap);
        newFactor.setRecipeMapReversed(recipeMap_r);
        newFactor.setIngredientMap(ingredientMap);
        newFactor.setIngredientMapReversed(ingredientMap_r);
        
        //deep copy
        List<User> userCopy = new ArrayList<User>();
        for(User curr: this.users){
            userCopy.add(new User(curr.getId()));
        }
        newFactor.setUsers(userCopy);
        newFactor.setDataset(dataset.copy());
        newFactor.updateInitialSpace();
        return newFactor;
    }
    
    protected boolean isPreprocessed(){
        boolean successReadingRecipes = this.readRecipesData();
        boolean successReadingUsers = this.readUsersData();
        if(successReadingUsers) updateInitialSpace();
        boolean successInitializingDataset = this.initializeDataset();
        System.out.println("Reading recipes data status: "+successReadingRecipes);
        System.out.println("Reading user data status   : "+successReadingUsers);
        System.out.println("Initializing dataset       : "+successInitializingDataset);
        return successReadingRecipes && successReadingUsers && successInitializingDataset;
    }
    
    private boolean readRecipesData() {
        boolean success = false;
        try {
            /* 
                this method only works in java IDE
            
                File file = new File("./data/dataset_recipes_ingredient_list_readable_java.csv");
                FileReader fr = new FileReader(fr);
                BufferedReader br = new BufferedReader(fr);
            
                to be usable in jar, look below. Thanks: https://stackoverflow.com/questions/20389255/reading-a-resource-file-from-within-jar
            */
            InputStream in = getClass().getResourceAsStream("/data/recipe_ingredient_list.csv");
            //pengujian
            //InputStream in = getClass().getResourceAsStream("/data_pengujian/dataset_recipes_ingredient_list_readable_java.csv");
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line = "";
            String[] tempArr;
            int index = 0;
            LinkedHashSet<String> ingredientsUniqueId = new LinkedHashSet<String>();
            while ((line = br.readLine()) != null) {
                tempArr = line.split("[|]+");
                Recipe curr = new Recipe(tempArr); 
                this.recipes.add(curr);
                this.recipeMap.put(curr.getId(), index);
                this.recipeMap_r.put(index, curr.getId());
                index++;
                ingredientsUniqueId.addAll(Arrays.asList(tempArr[3].split(",")));
            }
            br.close();
            index = 0;
            for (String uniqueId : ingredientsUniqueId) {
                this.ingredients.add(uniqueId);
                this.ingredientMap.put(uniqueId, index);
                this.ingredientMap_r.put(index, uniqueId);
                index++;
            }
            success = true;
        } catch (Exception ioe) {
            ioe.printStackTrace();
            System.exit(1);
        } finally {
            return success;
        }
    }

    private boolean readUsersData() {
        boolean success = false;
        try {
            InputStream in = getClass().getResourceAsStream("/data/userId_list.csv");
            // pengujian
            // InputStream in = getClass().getResourceAsStream("/data_pengujian/dataset_userId_list.csv");
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line = "";
            int index = 0;
            while ((line = br.readLine()) != null) {
                this.users.add(new User(line));
                this.userMap.put(line,index);
                this.userMap_r.put(index,line);
                index++;
            }
            br.close();
            success = true;
        } catch (Exception ioe) {
            ioe.printStackTrace();
        } finally {
            return success;
        }
    }
    
    public void updateInitialSpace(){this.INIT_USER_SPACE = this.users.size();}
    
    private boolean initializeDataset(){
        //first time Dataset d initialization
        this.dataset = new Dataset(this.users.size(),this.recipes.size());
        //1. Read Dataset
        boolean succesReadDataset = this.dataset.read(this.userMap,this.recipeMap);
        if (!succesReadDataset) return false;
        return true;
    }
    
    protected void addCustomUser(String userId, HashMap<String,String> customRating){
        try{
            double[] v = new double[this.recipes.size()];
            List<Pair> customePair = new ArrayList<Pair>();
            for(String id: customRating.keySet()){
                int recipeIndex = recipeMap.get(id);
                System.out.println(Double.parseDouble(customRating.get(id)));
                v[recipeIndex] = Double.parseDouble(customRating.get(id));
                customePair.add(new Pair(userId,id));
            }
            if(this.users.size() == INIT_USER_SPACE){
                //already done some custom recommendation before
                userMap.put(userId, this.users.size()); //if user space = 500, then max index at 499, so user.size give index 500
                userMap_r.put(this.users.size(),userId);
                //since userMap and userMap_r reference is from dataset d attribute, so setting them here is the same for the dataset attribute
                this.users.add(new User(userId));   
                this.dataset.addNewVector(v,customePair);
            } else if(this.users.size() < INIT_USER_SPACE) throw new Exception("User size is less than its initial size");
            else {
                int idx = userMap.get(userId);
                this.dataset.replaceCustomeUser(v, customePair, idx);
            }
        } catch(Exception e){
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    protected void setParameter(String... data){
        this.utils = new FactorizationUtil(
           Integer.parseInt(data[0]),
           Double.parseDouble(data[2]),
           data[4].equals("Fixed") ? Double.parseDouble(data[3]) : 1,
           this
        );
        this.MAX_LOOP = Integer.parseInt(data[1]);
        this.learningType = data[4].equals("Fixed") ? 0 : 1;
    }

    @Override
    public void run() {
        //set min obj value
        //SIZE OF OBJ * MIN.ERROR
        this.MIN_OBJECTIVE_VAL = this.dataset.getTrainPair().size()*0.5;
        this.mdc.insertLog("#1 Memecah Dataset\n");
        //1. Split Dataset
        boolean isSplitted = false;
        try {
            List<Object> split_mat = this.dataset.split(this.userMap,this.recipeMap);
            this.trainM = (TrainMatrix) split_mat.remove(0);
            this.testM = (TestMatrix) split_mat.remove(0);
            isSplitted = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (isSplitted) {
                this.mdc.insertLog("Berhasil memecah dataset.\n");
            } else {
                this.mdc.insertLog("Error: Terjadi kesalahan dalam memecah dataset. Lihat riwayat error untuk lebih detail.\n");
                System.exit(1);
                return;
            }
        }

        this.mdc.insertLog("#2 Inisialisasi Matriks Faktor\n");
        //2. Initialize Matrix Factor  
        boolean initializeFactor = false;
        FactorMatrix userFactor = null;
        FactorMatrix userFactor_2 = null;
        FactorMatrix recipeFactor = null;
        FactorMatrix ingredientFactor = null;
        FactorMatrix recipeIngredientsMap = null;
        try {
            // user factor = m x f
            userFactor = new FactorMatrix(this.trainM.getRowLength(), utils.getLatentSize(), FactorType.USER);
            System.out.println("user factor: "+this.trainM.getRowLength()+"x"+utils.getLatentSize());
            // recipe factor = n x f
            recipeFactor = new FactorMatrix(this.trainM.getColLength(), utils.getLatentSize(), FactorType.RECIPES);
            System.out.println("recipe factor: "+this.trainM.getColLength()+"x"+utils.getLatentSize());
            // ingredient factor = o x f
            ingredientFactor = new FactorMatrix(this.ingredients.size(), utils.getLatentSize(), FactorType.INGREDIENTS);
            System.out.println("ingredient factor: "+this.ingredients.size()+"x"+utils.getLatentSize());
            // recipe x ingredient map matrix = n x o (mask matrix 1/0)
            recipeIngredientsMap = new FactorMatrix(this.trainM.getColLength(),
                    this.ingredients.size(),
                    this.recipes,
                    this.recipeMap,
                    this.ingredientMap);
            System.out.println("recipe ingre map: "+this.trainM.getColLength()+"x"+this.ingredients.size());
            userFactor_2 = new FactorMatrix(userFactor);
            initializeFactor = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (initializeFactor) {
                this.mdc.insertLog("Berhasil menginisialisasi faktor matriks.\n");
            } else {
                this.mdc.insertLog("Error: Terjadi kesalahan dalam proses inisialisasi. Lihat riwayat error untuk lebih detail.\n");
                System.exit(1);
                return;
            }
        }
        
        //3. entering loop  
        //loop a thousand time or break if objective value less than MIN_OBJECTIVE_VAL
        boolean firstMethodOptimization = false;   
        boolean secondMethodOptimization = false;
        double objFuncPrev_1 = 0.0;    
        double objFuncPrev_2 = 0.0;
        
        this.mdc.insertLog("#3.1 Memasuki iterasi untuk optimisasi model matriks faktor pertama.\n"
                + "# Detail Proses No. 1:\n"
                + "# Maksimal Iterasi: " + MAX_LOOP + "\n"
                + "# Metode: Faktorisasi dengan dua buah matriks faktor, faktor Pengguna & Resep Makanan.\n"
        );
        long start = System.currentTimeMillis(); 
        String[] obj1_list = new String[1000];
        double[][] bestPrediction = null;
        try{
            double max_val = Double.MAX_VALUE;
            int loop = MAX_LOOP;
            while(loop-->0){
                if(this.learningType > 0) this.utils.setLearningRate(1.0/((MAX_LOOP-loop)*1.0)); //iteratively 
                //if(loop%250==0) {
                //    System.out.println("loop: "+(MAX_LOOP-loop));
                //}
                double[][] currPrediction = userFactor.multiply(recipeFactor.transpose());
                double objectiveValue = utils.objectiveFunction_m1(
                        userFactor,
                        recipeFactor,
                        this.dataset.getTrainPair()
                );
                
                if(objectiveValue < max_val){
                    bestPrediction = currPrediction;
                    max_val = objectiveValue;
                }
                
                obj1_list[MAX_LOOP-1-loop] = String.format("%.4f",objectiveValue);
                //if(loop%250==0) {
                //    System.out.println("Objective Function: "+objectiveValue);
                //}
                //System.out.println(objectiveValue);
                if(objectiveValue <= MIN_OBJECTIVE_VAL) break;
                else if (Math.abs(objFuncPrev_1 - objectiveValue) <= 0.00001) {
                    break;
                }
                else if(Double.isNaN(objectiveValue)) throw new RuntimeException("Objective Function value is NaN");
                else{
                    //do A-GD
                    utils.alternatingGradientDescent_m1(
                            userFactor,
                            recipeFactor,
                            this.dataset.getTrainPair()
                    );
                }   
            }
            firstMethodOptimization = true;
        } catch(Exception e){
            e.printStackTrace();
        } finally {
            if(firstMethodOptimization) this.mdc.insertLog("Proses optimisasi metode pertama selesai\n");
            else{ 
                this.mdc.insertLog("Error: Terjadi kesalahan dalam proses faktorisasi. Lihat riwayat error untuk lebih detail.\n");
                System.exit(1);
                return;
            }
        }
        long elapsedTime_1 = System.currentTimeMillis() - start;
//        try{
//            CSVWriter writer = new CSVWriter(new FileWriter("C:\\Users\\asus\\Desktop\\obj1.csv", false));
//            writer.writeNext(obj1_list);
//            writer.close();
//            this.mdc.insertLog("Model saved in csv\n");
//        } catch(Exception e) {
//            e.printStackTrace();
//        } 
        
        
//        this.mdc.insertLog("#3.2 Memasuki iterasi untuk optimisasi model matriks faktor kedua.\n"
//                + "# Detail Proses No. 2:\n"
//                + "# Maksimal Iterasi: " + MAX_LOOP + "\n"
//                + "# Metode: Faktorisasi dengan dua buah matriks faktor, faktor Pengguna & Bahan Makanan.\n"
//                + "          Serta memanfaatkan matriks biner yang memetakan resep & bahan makanan.\n"
//        );
//        start = System.currentTimeMillis();  
//        String[] obj2_list = new String[1000];
//        try {
//            int loop = MAX_LOOP;
//            while (loop-- > 0) {
//                //System.out.println("loop: " + (MAX_LOOP - loop));
//                if(this.learningType > 0) this.utils.setLearningRate(1.0/((MAX_LOOP-loop)*1.0)); //iteratively 
//                //MatrixUtil.sumAll(userFactor_2.getEntry());
//                //MatrixUtil.sumAll(ingredientFactor.getEntry());
//                double[][] currPrediction = MatrixUtil.multiplyWithTransposing(
//                        MatrixUtil.multiplyWithTransposing(userFactor_2.getEntry(), ingredientFactor.getEntry(), false),
//                        recipeIngredientsMap.getEntry(),
//                        false); 
//                        
//                double objectiveValue = utils.objectiveFunction_m2(
//                        currPrediction,
//                        userFactor_2,
//                        ingredientFactor,
//                        this.dataset.getTrainPair()
//                );
//                
//                obj2_list[MAX_LOOP-1-loop] = String.format("%.4f",objectiveValue);
//                //System.out.println("Objective Function: "+objectiveValue);
//                if (objectiveValue <= MIN_OBJECTIVE_VAL) {
//                    break;
//                } else if (Math.abs(objFuncPrev_2 - objectiveValue) <= 0.00001) {
//                    break;
//                } else if (Double.isNaN(objectiveValue)) {
//                    throw new RuntimeException("Objective Function value is NaN");
//                } else {
//                    //do A-GD
//                    objFuncPrev_2 = objectiveValue;
//                    utils.alternatingGradientDescent_m2(
//                            currPrediction,
//                            userFactor_2,
//                            ingredientFactor,
//                            recipeIngredientsMap,
//                            this.dataset.getTrainPair()
//                    );
//                }
//            }
//            secondMethodOptimization = true;
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            if (secondMethodOptimization) {
//                this.mdc.insertLog("Proses optimisasi metode kedua selesai.\n");
//            } else {
//                this.mdc.insertLog("Error: Terjadi kesalahan dalam proses faktorisasi. Lihat riwayat error untuk lebih detail.\n");
//                System.exit(1);
//                return;
//            }
//        }
//        long elapsedTime_2 = System.currentTimeMillis() - start;

//        try{
//            CSVWriter writer = new CSVWriter(new FileWriter("C:\\Users\\asus\\Desktop\\obj2.csv", false));
//            writer.writeNext(obj2_list);
//            writer.close();
//            this.mdc.insertLog("Model saved in csv\n");
//        } catch(Exception e) {
//            e.printStackTrace();
//        } 
        
        this.firstMethodRes = userFactor.multiply(recipeFactor.transpose());
//        this.secondMethodRes = MatrixUtil.multiplyWithTransposing(
//                MatrixUtil.multiplyWithTransposing(userFactor_2.getEntry(), ingredientFactor.getEntry(), false),
//                recipeIngredientsMap.getEntry(),
//                false); 

//        System.out.println(firstMethodRes.length +" x "+firstMethodRes[0].length);
//        System.out.println(secondMethodRes.length +" x "+secondMethodRes[0].length);
        
        double rmse_1_test = utils.rmse(
                this.dataset.getTestPair(),
                bestPrediction,
                this.testM.getEntry(),
                0
        );
        
        double rmse_1_train = utils.rmse(
                this.dataset.getTrainPair(),
                bestPrediction,
                this.trainM.getEntry(),
                0
        );
        
        double rmse_1_data = utils.rmse(
                this.dataset.getDataPair(),
                bestPrediction,
                this.dataset.getEntry(),
                0
        );
        
//        double rmse_2_test = utils.rmse(
//                this.dataset.getTestPair(),
//                secondMethodRes,
//                this.testM.getEntry(),
//                1
//        );
//        
//        double rmse_2_train = utils.rmse(
//                this.dataset.getTrainPair(),
//                secondMethodRes,
//                this.trainM.getEntry(),
//                1
//        );
//        
//        double rmse_2_data = utils.rmse(
//                this.dataset.getDataPair(),
//                secondMethodRes,
//                this.dataset.getEntry(),
//                1
//        );
        
        rmse_1[0] = rmse_1_test;
        rmse_1[1] = rmse_1_train;
        rmse_1[2] = rmse_1_data;
        
//        rmse_2[0] = rmse_2_test;
//        rmse_2[1] = rmse_2_train;
//        rmse_2[2] = rmse_2_data;
        
        time1 = elapsedTime_1;
        //time2 = elapsedTime_2;
        this.mdc.insertLog("Hasil RMSE:\n"
                + "############################\n"
                + "# Metode Faktorisasi Pertama\n"
                + String.format("# RMSE (Test  Set) = %.3f\n",rmse_1_test)
                + String.format("# RMSE (Train Set) = %.3f\n",rmse_1_train)
                + String.format("# RMSE (Data  Set) = %.3f\n",rmse_1_data)
                + String.format("# Waktu Berjalan: %.3fs\n", elapsedTime_1/1000F)
                + "############################\n"
                + "\n"
//                + "############################\n"
//                + "# SECOND OPTIMIZATION METHOD\n"
//                + String.format("# RMSE (Test  Set) = %.5f\n",rmse_2_test)
//                + String.format("# RMSE (Train Set) = %.5f\n",rmse_2_train)
//                + String.format("# RMSE (Data  Set) = %.5f\n",rmse_2_data)
//                + String.format("# Waktu Berjalan: %.3fs\n", elapsedTime_2/1000F)
//                + "############################\n"
//                + "\n"
                + "Seluruh proeses faktorisasi telah selesai.\n");
    }
    
    protected List<RecipePredicted> getUserPrediction(String userId){
        System.out.println(userId);
        int userIndex = this.userMap.get(userId);
        List<RecipePredicted> result = new ArrayList<RecipePredicted>();
        try{
            for (Recipe curr: this.recipes) {
                int recipeIndex = this.recipeMap.get(curr.getId());
                result.add(new RecipePredicted(
                        curr.getName(),
                        String.format("%.2f",this.dataset.getEntryByIndex(userIndex, recipeIndex)),
                        String.format("%.2f",firstMethodRes[userIndex][recipeIndex]/(utils.getLatentSize()*5)),
                        String.format("%.2f",secondMethodRes[userIndex][recipeIndex]/(utils.getLatentSize()*5*curr.getIngredientLength()))
                ));
            }
        } catch(Exception e){
            e.printStackTrace();
        } finally {
            return result;   
        }
    }

    public void setDataset(Dataset d) {
        this.dataset = d;
    }

    public List<Recipe> getRecipes() {
        return recipes;
    }

    public void setRecipes(List<Recipe> recipes) {
        this.recipes = recipes;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public void setIngredients(List<String> ingredients) {
        this.ingredients = ingredients;
    }

    public void setUserMap(HashMap<String, Integer> userMap) {
        this.userMap = userMap;
    }

    public void setRecipeMap(HashMap<String, Integer> recipeMap) {
        this.recipeMap = recipeMap;
    }

    public void setIngredientMap(HashMap<String, Integer> ingredientMap) {
        this.ingredientMap = ingredientMap;
    }

    public void setUserMapReversed(HashMap<Integer, String> userMap_r) {
        this.userMap_r = userMap_r;
    }

    public void setRecipeMapReversed(HashMap<Integer, String> recipeMap_r) {
        this.recipeMap_r = recipeMap_r;
    }

    public void setIngredientMapReversed(HashMap<Integer, String> ingredientMap_r) {
        this.ingredientMap_r = ingredientMap_r;
    }
    
    public int getRecipeSize(){return this.recipes.size();}

    @Override
    public List<String> getIngredient() {
        return ingredients;
    }

    @Override
    public TrainMatrix getTrainMatrix() {
        return trainM;
    }

    @Override
    public TestMatrix getTestMatrix() {
        return testM;
    }

    @Override
    public HashMap<String, Integer> getUserMap() {
        return userMap;
    }

    @Override
    public HashMap<String, Integer> getRecipeMap() {
        return recipeMap;
    }

    @Override
    public HashMap<String, Integer> getIngredientMap() {
        return ingredientMap;
    }

    @Override
    public HashMap<Integer, String> getUserMapReversed() {
        return userMap_r;
    }

    @Override
    public HashMap<Integer, String> getRecipeMapReversed() {
        return recipeMap_r;
    }

    @Override
    public HashMap<Integer, String> getIngredientMapReversed() {
        return ingredientMap_r;
    }
    
    /*
    getter for temporary attributes for automatic testing purpose, delete later
    */
    public double[] getFirstRMSE(){return this.rmse_1;}
    public double[] getSecondRMSE(){return this.rmse_2;}
    public long getTime(int type){
        if(type==1) return time1;
        else return time2;
    }
}
