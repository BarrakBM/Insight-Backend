-- Create users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    fullname TEXT NOT NULL,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    fcm_token TEXT
);

-- Create MCC (Merchant Category Codes) table
CREATE TABLE mcc (
    id BIGSERIAL PRIMARY KEY,
    code TEXT NOT NULL,
    category TEXT NOT NULL,
    sub_category TEXT
);

-- Create accounts table
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    account_type TEXT NOT NULL,
    userid BIGINT NOT NULL,
    account_number TEXT NOT NULL,
    balance DECIMAL(9,3) DEFAULT 0.000,
    card_number TEXT,
    
    CONSTRAINT fk_accounts_userid 
        FOREIGN KEY (userid) REFERENCES users(id) 
        ON DELETE CASCADE ON UPDATE CASCADE
);

-- Create transactions table
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    amount DECIMAL NOT NULL,
    transaction_type VARCHAR(50),
    mcc_id BIGINT,
    createdAt DATE DEFAULT CURRENT_DATE,
    
    CONSTRAINT fk_transactions_account_id 
        FOREIGN KEY (account_id) REFERENCES accounts(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_transactions_mcc_id 
        FOREIGN KEY (mcc_id) REFERENCES mcc(id) 
        ON DELETE SET NULL ON UPDATE CASCADE
);

-- Create limits table (note: fixed typo from 'acount_id' to 'account_id')
CREATE TABLE limits (
    id BIGSERIAL PRIMARY KEY,
    category TEXT NOT NULL,
    amount DECIMAL(9,3) NOT NULL,
    account_id BIGINT NOT NULL,
    
    CONSTRAINT fk_limits_account_id 
        FOREIGN KEY (account_id) REFERENCES accounts(id) 
        ON DELETE CASCADE ON UPDATE CASCADE
);

-- Create offers table
CREATE TABLE offers (
    id BIGSERIAL PRIMARY KEY,
    mcc_category_id BIGINT NOT NULL,
    description TEXT NOT NULL,
    
    CONSTRAINT fk_offers_mcc_category_id 
        FOREIGN KEY (mcc_category_id) REFERENCES mcc(id) 
        ON DELETE CASCADE ON UPDATE CASCADE
);