package com.pedropalazon.sales_manager_backend.customers.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void givenFormattedPhoneAndBlankNotes_whenSaved_thenPhoneIsNormalizedAndNotesAreNull() {
        Customer saved = repository.save(new Customer("  Ana Pérez ", "+34 600-123.456", "   "));
        entityManager.flush();
        entityManager.clear();

        Customer found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("Ana Pérez");
        assertThat(found.getPhone()).isEqualTo("+34600123456");
        assertThat(found.getNotes()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void givenCustomers_whenSearchingByNamePart_thenMatchesIgnoringCaseSortedByName() {
        repository.save(new Customer("Mariano López", "600111222", null));
        repository.save(new Customer("Ana Pérez", "600333444", null));
        repository.save(new Customer("Juan García", "600555666", null));

        assertThat(repository.search("AN", ""))
                .extracting(Customer::getName)
                .containsExactly("Ana Pérez", "Juan García", "Mariano López");
        assertThat(repository.search("pérez", ""))
                .extracting(Customer::getName)
                .containsExactly("Ana Pérez");
    }

    @Test
    void givenCustomers_whenSearchingByPhoneDigits_thenMatchesThePhone() {
        repository.save(new Customer("Ana Pérez", "+34 600 333 444", null));
        repository.save(new Customer("Juan García", "600555666", null));

        assertThat(repository.search("333 444", "333444"))
                .extracting(Customer::getName)
                .containsExactly("Ana Pérez");
    }

    @Test
    void givenTextWithoutDigits_whenSearching_thenDoesNotMatchEveryPhone() {
        repository.save(new Customer("Ana Pérez", "600333444", null));
        repository.save(new Customer("Juan García", "600555666", null));

        assertThat(repository.search("zzz", "")).isEmpty();
    }

    @Test
    void givenSavedCustomer_whenFindingByNormalizedPhone_thenFindsIt() {
        Customer saved = repository.save(new Customer("Ana Pérez", "600 333 444", null));

        assertThat(repository.findByPhone("600333444")).map(Customer::getId).contains(saved.getId());
    }
}
