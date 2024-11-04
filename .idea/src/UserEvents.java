import java.util.ArrayList;
import java.util.List;

public class UserEvents {
    private List<String> savedEvents;

    public UserEvents() {
        savedEvents = new ArrayList<>();
    }

    public void addEvent(String event) {
        savedEvents.add(event);
    }

    public List<String> getSavedEvents() {
        return savedEvents;
    }
}
