package org.fusesource.fabric.utils.shell;

import java.io.IOException;
import java.security.AccessController;
import java.util.Set;
import javax.security.auth.Subject;
import jline.Terminal;
import jline.console.ConsoleReader;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.jaas.boot.principal.UserPrincipal;
import org.fusesource.jansi.Ansi;

public class ShellUtils {

    private static final String FABRIC_USER = "FABRIC_USER";
    private static final String FABRIC_USER_PASSWORD = "FABRIC_USER_PASSWORD";
    private static final String FABRIC_ZOOKEEPER_PASSWORD = "FABRIC_ZOOKEEPER_PASSWORD";

    private ShellUtils() {
        //Utility Class
    }

    /**
     * Stores username and password to the {@link CommandSession}.
     * @param session
     * @param username
     * @param password
     */
    public static void storeFabricCredentials(CommandSession session, String username, String password) {
        session.put(FABRIC_USER, username);
        session.put(FABRIC_USER_PASSWORD, password);
    }

    /**
     * Stores zookeeper password to the {@link CommandSession}.
     * This is mostly usable when creating a remote ensemble that is going to be joined right after its creation.
     * @param session
     * @param zookeeperPassword
     */
    public static void storeZookeeperPassword(CommandSession session, String zookeeperPassword) {
         session.put(FABRIC_ZOOKEEPER_PASSWORD, zookeeperPassword);
    }

    /**
     * Returns the fabric username stored in the {@link CommandSession}.
     * @param session
     * @return
     */
    public static String retrieveFabricUser(CommandSession session) {
        Subject subject = Subject.getSubject(AccessController.getContext());
        if (!subject.getPrivateCredentials(String.class).isEmpty() && subject.getPrincipals(UserPrincipal.class) != null && !subject.getPrincipals(UserPrincipal.class).isEmpty()) {
            Set<UserPrincipal> userPrincipals = subject.getPrincipals(UserPrincipal.class);
            UserPrincipal userPrincipal = userPrincipals.iterator().next();
            return userPrincipal.getName();
        }

        if (session != null && session.get(FABRIC_USER) != null) {
            return (String) session.get(FABRIC_USER);
        }
        return null;
    }


    /**
     * Returns the fabric username stored in the {@link CommandSession}.
     * @param session
     * @return
     */
    public static String retrieveFabricUserPassword(CommandSession session) {
        Subject subject = Subject.getSubject(AccessController.getContext());
        if (!subject.getPrivateCredentials(String.class).isEmpty() && !subject.getPrincipals(UserPrincipal.class).isEmpty()) {
            return subject.getPrivateCredentials(String.class).iterator().next();
        }
        if (session != null && session.get(FABRIC_USER_PASSWORD) != null) {
            return (String) session.get(FABRIC_USER_PASSWORD);
        }
        return null;
    }

    /**
     * Returns the fabric zookeeper password stored in the {@link CommandSession}.
     * @param session
     * @return
     */
    public static String retrieveFabricZookeeperPassword(CommandSession session) {
        if (session != null && session.get(FABRIC_USER_PASSWORD) != null) {
            return (String) session.get(FABRIC_USER_PASSWORD);
        }
        return null;
    }

    /**
     * Displays the message and reads the input.
     * @param session   The {@link CommandSession} to use.
     * @param msg       The message to display.
     * @param hidden    Flag to hide the user input.
     * @return
     * @throws IOException
     */
    public static String readLine(CommandSession session, String msg, boolean hidden) throws IOException {
        Object obj = session.get(".jline.reader");
        if (obj instanceof ConsoleReader) {
            ConsoleReader reader = (ConsoleReader) obj;
            if (hidden) {
                return reader.readLine(msg,ConsoleReader.NULL_MASK);
            } else {
                return reader.readLine(msg);
            }
        }

        return null;
    }

    /**
     * Returns the {@Terminal} width.
     * @param session
     * @return
     */
    public static int getTermWidth(CommandSession session) {
        Terminal term = (Terminal) session.get(".jline.terminal");
        return term != null ? term.getWidth() : 80;
    }
}
