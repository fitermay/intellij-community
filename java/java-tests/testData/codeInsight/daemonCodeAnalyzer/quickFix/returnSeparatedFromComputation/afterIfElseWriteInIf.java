// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    int f(boolean b) {
        int n = 0;
        if (b) return 1;
        else System.out.println("no");
        return n;
    }
}