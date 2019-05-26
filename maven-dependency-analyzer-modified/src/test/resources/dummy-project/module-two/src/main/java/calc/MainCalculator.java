package calc;

import calc.Calculator;
import calc.CalculatorInt;

public class MainCalculator {

    public static void main(String[] args) {
        CalculatorInt calculatorInt = new Calculator(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        System.out.println("The sum is: " + calculatorInt.sum());
    }
}
