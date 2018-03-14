/* TypeLinking:
 */
package Main.B;

public class A {
    public A() {}
    
    public static Main.B.A getInstance() {
	return new Main.B.A();
    }
}
