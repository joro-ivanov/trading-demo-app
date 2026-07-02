create table users (
    id            varchar(64)  primary key,
    username      varchar(20)  not null unique,
    email         varchar(255) not null unique,
    display_name  varchar(100) not null,
    password_hash varchar(72)  not null
);

-- Seed the three demo users. password_hash is bcrypt('password') so existing logins keep working.
insert into users (id, username, email, display_name, password_hash) values
    ('u1', 'alice', 'alice@demo.example.com', 'Alice Trader',
        '$2a$10$1Wx0LDGYW3tXdeaROynRz./qi5Dr4RNxjBNbJBMlBfEhIqO057386'),
    ('u2', 'bob', 'bob@demo.example.com', 'Bob Trader',
        '$2a$10$1Wx0LDGYW3tXdeaROynRz./qi5Dr4RNxjBNbJBMlBfEhIqO057386'),
    ('u3', 'demo', 'demo@demo.example.com', 'Demo User',
        '$2a$10$1Wx0LDGYW3tXdeaROynRz./qi5Dr4RNxjBNbJBMlBfEhIqO057386');
