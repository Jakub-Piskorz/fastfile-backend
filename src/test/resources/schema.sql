INSERT INTO _user (id,
                   email,
                   first_name,
                   last_name,
                   password,
                   username,
                   used_storage,
                   user_type)
VALUES (-1,
        'example@example.com',
        'testFirstname',
        'testLastname',
           -- Actual password: "secretPassword"
        '$2a$10$M8OifbnBh7y9PyOrTObzS.cq6MuOFR87dws9cET6MajFMjWQnJs9u',
        'testUser',
        0,
        'free');
