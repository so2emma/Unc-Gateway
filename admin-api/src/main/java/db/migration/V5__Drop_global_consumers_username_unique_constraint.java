package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Drops the global {@code UNIQUE} constraint that V1 declared on {@code consumers.username}.
 * <p>
 * Under the shared-schema pool multi-tenancy model, uniqueness must be per tenant — V4 added the
 * {@code (tenant_id, username)} unique index for that. The old constraint was created inline in the
 * {@code CREATE TABLE} statement, so its name is database-generated ({@code consumers_username_key}
 * on PostgreSQL, {@code CONSTRAINT_xx} on H2) and cannot be dropped portably by name. This migration
 * resolves the actual name from the information schema, which both engines expose identically.
 */
public class V5__Drop_global_consumers_username_unique_constraint extends BaseJavaMigration {

    private static final String FIND_SINGLE_COLUMN_UNIQUE_CONSTRAINTS_ON_USERNAME = """
            SELECT tc.constraint_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
             AND tc.table_name = kcu.table_name
            WHERE tc.table_name = 'consumers'
              AND tc.constraint_type = 'UNIQUE'
              AND kcu.column_name = 'username'
            """;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        List<String> constraintNames = new ArrayList<>();
        try (PreparedStatement statement =
                     connection.prepareStatement(FIND_SINGLE_COLUMN_UNIQUE_CONSTRAINTS_ON_USERNAME);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                constraintNames.add(resultSet.getString("constraint_name"));
            }
        }

        for (String constraintName : constraintNames) {
            if (!isSingleColumnConstraint(connection, constraintName)) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE consumers DROP CONSTRAINT \"" + constraintName + "\"");
            }
        }
    }

    /** Guards against dropping a composite constraint that merely includes {@code username}. */
    private boolean isSingleColumnConstraint(Connection connection, String constraintName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_name = 'consumers' AND constraint_name = ?")) {
            statement.setString(1, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }
}
