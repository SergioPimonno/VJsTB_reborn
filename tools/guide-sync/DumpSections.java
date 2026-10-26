import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.ContentSection;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/** Выгружает DEFAULT_SECTIONS руководства и приветствия в JSON {"sections":[{title,bodyHtml}]}. */
public class DumpSections {
    public static void main(String[] args) throws Exception {
        ObjectMapper m = new ObjectMapper();
        dump(m, Class.forName("com.vjstb.ledscheme.ui.GuideDialog"), new File(args[0], "GUIDE_NEW.json"), false);
        dump(m, Class.forName("com.vjstb.ledscheme.ui.OnboardingDialog"), new File(args[0], "ONBOARDING_NEW.json"), true);
    }

    @SuppressWarnings("unchecked")
    static void dump(ObjectMapper m, Class<?> c, File out, boolean stripVersion) throws Exception {
        Field f = c.getDeclaredField("DEFAULT_SECTIONS");
        f.setAccessible(true);
        List<ContentSection> sections = (List<ContentSection>) f.get(null);
        var list = new java.util.ArrayList<Map<String, String>>();
        for (ContentSection s : sections) {
            String title = s.getTitle();
            if (stripVersion && title.startsWith("AVE_ToolBox v")) {
                title = "AVE_ToolBox";
            }
            list.add(Map.of("title", title, "bodyHtml", s.getBodyHtml()));
        }
        m.writerWithDefaultPrettyPrinter().writeValue(out, Map.of("sections", list));
        System.out.println(out + ": " + list.size() + " sections");
    }
}
