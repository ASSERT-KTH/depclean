package bag;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;

public class BagClass {

    public void main() {
        Bag<String> bag = new HashBag<>();

        //add "a" two times to the bag.
        bag.add("a", 2);

        //add "b" one time to the bag.
        bag.add("b");

        //add "c" one time to the bag.
        bag.add("c");

        //add "d" three times to the bag.
        bag.add("d", 3);

        System.out.println("Set of unique values from the bag: " + bag.uniqueSet());
    }
}