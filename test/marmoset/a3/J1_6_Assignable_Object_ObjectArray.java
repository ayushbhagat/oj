// TYPE_CHECKING,CODE_GENERATION
public class J1_6_Assignable_Object_ObjectArray {

    public J1_6_Assignable_Object_ObjectArray () {}

    public static int test() {
	Object j = new Object[123];
	return ((Object[])j).length;
    }

}
