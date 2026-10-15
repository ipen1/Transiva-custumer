package com.transiva.app;

/** Pure color policy, shared with regression tests. Alpha is kept separate from color meaning. */
final class CustomerThemeColors {
    static final int PAGE=0xff091625, CARD=0xff122235, SOFT=0xff172b42, BORDER=0xff2d435c;
    static final int TEXT=0xffeef5ff, MUTED=0xffb8c8db, ACCENT=0xff78baff;
    static final int LIGHT_PAGE=0xffeaf1fa, LIGHT_SOFT=0xffe3edfa;
    private CustomerThemeColors() { }
    static int r(int c){return c>>16&255;}static int g(int c){return c>>8&255;}static int b(int c){return c&255;}
    static int alpha(int c){return c>>>24;}
    static int composite(int fg,int bg) {
        int a=alpha(fg), inv=255-a;
        return 0xff000000|((r(fg)*a+r(bg)*inv)/255)<<16|((g(fg)*a+g(bg)*inv)/255)<<8|(b(fg)*a+b(bg)*inv)/255;
    }
    static int surface(int c,int depth) {
        if(alpha(c)<180)return c;
        int max=Math.max(r(c),Math.max(g(c),b(c))), min=Math.min(r(c),Math.min(g(c),b(c)));
        if(min<170)return c; // Preserve brand surfaces, media, and existing dark surfaces.
        if(r(c)>g(c)+8 && g(c)>b(c)+8)return 0xff3c2d19; // warm/gold
        if(g(c)>r(c)+7 && g(c)>b(c)+3)return 0xff12382f; // positive
        if(r(c)>g(c)+4 && r(c)>b(c)+2)return 0xff40232d; // warning/error
        if(max-min<75)return depth<=2?PAGE:(min>235?CARD:SOFT);
        return c;
    }
    /** Restrict light refinements to neutral blue surfaces; never alter semantic or brand fills. */
    static int lightSurface(int c,int depth) {
        if(alpha(c)!=255)return c;
        if(c==0xfff5f8fd||c==0xfff4f8fd||c==0xfff5f9ff||c==0xfff4f8ff)return depth<=2?LIGHT_PAGE:LIGHT_SOFT;
        if(c==0xfff8fbff||c==0xfff2f8ff||c==0xfff3f7ff||c==0xffeaf4ff)return LIGHT_SOFT;
        return c;
    }
    static int lightText(int c,int bg) {
        if(alpha(c)<180||luminance(bg)<.6||contrast(composite(c,bg),bg)>=4.5)return c;
        int spread=Math.max(r(c),Math.max(g(c),b(c)))-Math.min(r(c),Math.min(g(c),b(c)));
        if(spread<65)return contrast(0xff52647c,bg)>=4.5?0xff52647c:0xff123555;
        if(b(c)>r(c)*1.3&&b(c)>g(c)*1.1)return 0xff0964d6;
        return c;
    }
    static int brand(int c) {
        if(alpha(c)<180 || b(c)<g(c)*1.05 || g(c)<r(c)*1.3)return c;
        int next=c;
        for(int i=0;i<16 && contrast(0xffffffff,next)<4.5;i++)
            next=0xff000000|((int)(r(next)*.92)<<16)|((int)(g(next)*.92)<<8)|(int)(b(next)*.92);
        return next;
    }
    static double luminance(int c) {
        return .2126*linear(r(c)/255d)+.7152*linear(g(c)/255d)+.0722*linear(b(c)/255d);
    }
    private static double linear(double x){return x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}
    static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    static int text(int original,int bg) {
        int shown=composite(original,bg);
        if(contrast(shown,bg)>=4.5)return original;
        // Keep semantic colors recognizable, but brighten them against dark panels.
        int candidate;
        if(r(original)>b(original)*1.3 && g(original)>b(original)*1.1)candidate=0xffffdfa1;
        else if(r(original)>g(original)*1.25 && r(original)>b(original)*1.15)candidate=0xffffa9b4;
        else if(g(original)>r(original)*1.15 && g(original)>b(original)*1.08)candidate=0xff8ee5bc;
        else if(b(original)>r(original)*1.3 && b(original)>g(original)*1.1)candidate=ACCENT;
        else candidate=luminance(original)>.16?MUTED:TEXT;
        if(contrast(candidate,bg)>=4.5)return candidate;
        return contrast(0xffffffff,bg)>contrast(0xff102139,bg)?0xffffffff:0xff102139;
    }
}
