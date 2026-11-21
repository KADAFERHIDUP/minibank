package io.github.kadaferhidup.minibank.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    /**
     * Membaca pengguna sambil mengunci barisnya (SELECT ... FOR UPDATE) sampai transaksi selesai.
     * Dipakai untuk hitungan PIN salah dan batas jumlah rekening, supaya permintaan paralel
     * tidak bisa membaca angka lama lalu sama-sama lolos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
