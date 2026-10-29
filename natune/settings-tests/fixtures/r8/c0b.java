package r8;
import java.util.List;
/** Host-only stand-in for the original settings-row ABI and default mask. */
public final class c0b {
    public final String a;public final int b;public final String c,d,e;public final boolean f;
    public final long g;public final List<?> h,i;public final si4 j;public final boolean k;
    public c0b(String key,int icon,String title,String subtitle,String badge,boolean update,long color,
            List<?> keywords,List<?> children,si4 click,int mask){
        a=key;b=icon;c=title;d=subtitle;e=badge;f=update;g=color;h=keywords;i=children;j=click;k=(mask&2048)==0;
    }
}
