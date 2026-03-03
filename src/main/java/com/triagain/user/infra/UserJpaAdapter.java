package com.triagain.user.infra;

import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserJpaAdapter implements UserRepositoryPort {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        UserJpaEntity entity = UserJpaEntity.fromDomain(user);
        return userJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<User> findById(String id) {
        return userJpaRepository.findById(id)
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public List<User> findAllByIds(List<String> ids) {
        return userJpaRepository.findAllById(ids).stream()
                .map(UserJpaEntity::toDomain)
                .toList();
    }
}
