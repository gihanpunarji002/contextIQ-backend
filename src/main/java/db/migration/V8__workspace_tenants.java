package db.migration;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.flywaydb.core.api.migration.*;

/** Drops the old account-wide name constraint portably before applying the tenant schema. */
public class V8__workspace_tenants extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var names = new ArrayList<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT constraint_name FROM information_schema.table_constraints WHERE LOWER(table_name)='document_folders' AND constraint_type='UNIQUE'")) {
            while (rows.next()) names.add(rows.getString(1));
        }
        for (String name : names) {
            var columns = new HashSet<String>();
            try (var statement = connection.prepareStatement("SELECT column_name FROM information_schema.key_column_usage WHERE constraint_name=?")) {
                statement.setString(1, name);
                try (var rows = statement.executeQuery()) { while (rows.next()) columns.add(rows.getString(1).toLowerCase(Locale.ROOT)); }
            }
            if (columns.equals(Set.of("owner_id", "name"))) try (var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE document_folders DROP CONSTRAINT \"" + name.replace("\"", "\"\"") + "\"");
            }
        }
        try (var resource = Objects.requireNonNull(getClass().getResourceAsStream("/db/schema/workspaces.sql"))) {
            for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) try (var statement = connection.createStatement()) { statement.execute(sql); }
            }
        }
    }
}
