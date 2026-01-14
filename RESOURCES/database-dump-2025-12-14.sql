--
-- PostgreSQL database dump
--

\restrict b7kmrAcMtnvcIBwH3kmW3KLyvtobAtk9CSseEABnsa2UDA7lgH8Uwzmm4X65mSW

-- Dumped from database version 17.7 (178558d)
-- Dumped by pg_dump version 17.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: user_role; Type: TYPE; Schema: public; Owner: neondb_owner
--

CREATE TYPE public.user_role AS ENUM (
    'admin',
    'user'
);


ALTER TYPE public.user_role OWNER TO neondb_owner;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: bank_accounts; Type: TABLE; Schema: public; Owner: neondb_owner
--

CREATE TABLE public.bank_accounts (
    id integer NOT NULL,
    user_id integer NOT NULL,
    bank_name character varying(255) NOT NULL,
    account_number character varying(50) NOT NULL,
    account_holder_name character varying(255) NOT NULL,
    account_type character varying(50) DEFAULT 'savings'::character varying,
    balance numeric(15,2) DEFAULT 0,
    is_primary boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.bank_accounts OWNER TO neondb_owner;

--
-- Name: bank_accounts_id_seq; Type: SEQUENCE; Schema: public; Owner: neondb_owner
--

CREATE SEQUENCE public.bank_accounts_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.bank_accounts_id_seq OWNER TO neondb_owner;

--
-- Name: bank_accounts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: neondb_owner
--

ALTER SEQUENCE public.bank_accounts_id_seq OWNED BY public.bank_accounts.id;


--
-- Name: cards; Type: TABLE; Schema: public; Owner: neondb_owner
--

CREATE TABLE public.cards (
    id integer NOT NULL,
    user_id integer NOT NULL,
    card_name character varying(255) NOT NULL,
    card_number_last4 character varying(4) NOT NULL,
    card_type character varying(50) NOT NULL,
    card_brand character varying(50),
    expiry_month integer,
    expiry_year integer,
    credit_limit numeric(15,2),
    current_balance numeric(15,2) DEFAULT 0,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.cards OWNER TO neondb_owner;

--
-- Name: cards_id_seq; Type: SEQUENCE; Schema: public; Owner: neondb_owner
--

CREATE SEQUENCE public.cards_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.cards_id_seq OWNER TO neondb_owner;

--
-- Name: cards_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: neondb_owner
--

ALTER SEQUENCE public.cards_id_seq OWNED BY public.cards.id;


--
-- Name: categories; Type: TABLE; Schema: public; Owner: neondb_owner
--

CREATE TABLE public.categories (
    id integer NOT NULL,
    name character varying(100) NOT NULL
);


ALTER TABLE public.categories OWNER TO neondb_owner;

--
-- Name: categories_id_seq; Type: SEQUENCE; Schema: public; Owner: neondb_owner
--

CREATE SEQUENCE public.categories_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.categories_id_seq OWNER TO neondb_owner;

--
-- Name: categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: neondb_owner
--

ALTER SEQUENCE public.categories_id_seq OWNED BY public.categories.id;


--
-- Name: expenses; Type: TABLE; Schema: public; Owner: neondb_owner
--

CREATE TABLE public.expenses (
    id integer NOT NULL,
    amount numeric(12,2) NOT NULL,
    date date NOT NULL,
    category_id integer NOT NULL,
    note text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    user_id integer NOT NULL
);


ALTER TABLE public.expenses OWNER TO neondb_owner;

--
-- Name: expenses_id_seq; Type: SEQUENCE; Schema: public; Owner: neondb_owner
--

CREATE SEQUENCE public.expenses_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.expenses_id_seq OWNER TO neondb_owner;

--
-- Name: expenses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: neondb_owner
--

ALTER SEQUENCE public.expenses_id_seq OWNED BY public.expenses.id;


--
-- Name: login_attempts; Type: TABLE; Schema: public; Owner: neondb_owner
--

CREATE TABLE public.login_attempts (
    id integer NOT NULL,
    email character varying(255) NOT NULL,
    attempted_at timestamp with time zone,
    success boolean DEFAULT false NOT NULL
);


ALTER TABLE public.login_attempts OWNER TO neondb_owner;

--
-- Name: login_attempts_id_seq; Type: SEQUENCE; Schema: public; Owner: neondb_owner
--

CREATE SEQUENCE public.login_attempts_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.login_attempts_id_seq OWNER TO neondb_owner;

--
-- Name: login_attempts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: neondb_owner
--

ALTER SEQUENCE public.login_attempts_id_seq OWNED BY public.login_attempts.id;


--
-- Name: sessions; Type: TABLE; Schema: public; Owner: neondb_owner
--

CREATE TABLE public.sessions (
    id character varying(64) NOT NULL,
    user_id integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_accessed timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL
);


ALTER TABLE public.sessions OWNER TO neondb_owner;

--
-- Name: user_passwords; Type: TABLE; Schema: public; Owner: neondb_owner
--

CREATE TABLE public.user_passwords (
    user_id integer NOT NULL,
    password_hash character varying(255) NOT NULL
);


ALTER TABLE public.user_passwords OWNER TO neondb_owner;

--
-- Name: users; Type: TABLE; Schema: public; Owner: neondb_owner
--

CREATE TABLE public.users (
    id integer NOT NULL,
    email character varying(255) NOT NULL,
    display_name character varying(255) NOT NULL,
    avatar_url text,
    role public.user_role DEFAULT 'user'::public.user_role NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    phone character varying(20),
    address text,
    currency character varying(10) DEFAULT 'USD'::character varying
);


ALTER TABLE public.users OWNER TO neondb_owner;

--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: neondb_owner
--

CREATE SEQUENCE public.users_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.users_id_seq OWNER TO neondb_owner;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: neondb_owner
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: bank_accounts id; Type: DEFAULT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.bank_accounts ALTER COLUMN id SET DEFAULT nextval('public.bank_accounts_id_seq'::regclass);


--
-- Name: cards id; Type: DEFAULT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.cards ALTER COLUMN id SET DEFAULT nextval('public.cards_id_seq'::regclass);


--
-- Name: categories id; Type: DEFAULT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.categories ALTER COLUMN id SET DEFAULT nextval('public.categories_id_seq'::regclass);


--
-- Name: expenses id; Type: DEFAULT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.expenses ALTER COLUMN id SET DEFAULT nextval('public.expenses_id_seq'::regclass);


--
-- Name: login_attempts id; Type: DEFAULT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.login_attempts ALTER COLUMN id SET DEFAULT nextval('public.login_attempts_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Data for Name: bank_accounts; Type: TABLE DATA; Schema: public; Owner: neondb_owner
--

COPY public.bank_accounts (id, user_id, bank_name, account_number, account_holder_name, account_type, balance, is_primary, created_at) FROM stdin;
\.


--
-- Data for Name: cards; Type: TABLE DATA; Schema: public; Owner: neondb_owner
--

COPY public.cards (id, user_id, card_name, card_number_last4, card_type, card_brand, expiry_month, expiry_year, credit_limit, current_balance, is_active, created_at) FROM stdin;
\.


--
-- Data for Name: categories; Type: TABLE DATA; Schema: public; Owner: neondb_owner
--

COPY public.categories (id, name) FROM stdin;
1	Food
2	Transport
3	Shopping
4	Entertainment
5	Bills
6	Health
7	Education
8	Other
\.


--
-- Data for Name: expenses; Type: TABLE DATA; Schema: public; Owner: neondb_owner
--

COPY public.expenses (id, amount, date, category_id, note, created_at, user_id) FROM stdin;
1	5.00	2025-12-14	7		2025-12-14 09:29:46.364206+00	1
2	4.99	2025-12-14	7	hkg	2025-12-14 10:04:44.079764+00	2
\.


--
-- Data for Name: login_attempts; Type: TABLE DATA; Schema: public; Owner: neondb_owner
--

COPY public.login_attempts (id, email, attempted_at, success) FROM stdin;
1	ra@et.com	2025-12-14 10:03:51.194+00	t
\.


--
-- Data for Name: sessions; Type: TABLE DATA; Schema: public; Owner: neondb_owner
--

COPY public.sessions (id, user_id, created_at, last_accessed, expires_at) FROM stdin;
06003214c2b4e2ccb8528759ca16a69ebe75b594d2a568d85995119ec69e7757	2	2025-12-14 10:03:35.829+00	2025-12-14 10:03:35.829+00	2025-12-21 10:03:35.829+00
d81f25b089ca001a94e5f7cfe59bebb62f62ace53d7179cd59c3abf957508e55	2	2025-12-14 10:03:51.194+00	2025-12-14 10:18:59.871+00	2025-12-21 10:03:51.194+00
\.


--
-- Data for Name: user_passwords; Type: TABLE DATA; Schema: public; Owner: neondb_owner
--

COPY public.user_passwords (user_id, password_hash) FROM stdin;
1	$2b$10$I15DFEAiZuk8bKylQiRjSeGoccVJtzA8CMMB.eNaE.s8s4e.juMty
2	$2b$10$DMz559dJGTQ6WTitCQ8RM.CxyYxELiXBYMDNy97YLLWphKoICVmM.
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: neondb_owner
--

COPY public.users (id, email, display_name, avatar_url, role, created_at, phone, address, currency) FROM stdin;
1	aziz.riduan76@gmail.com	Test User	\N	user	2025-12-14 09:56:39.402792+00	\N	\N	USD
2	ra@et.com	RA	\N	user	2025-12-14 10:03:35.803734+00	\N	\N	USD
\.


--
-- Name: bank_accounts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: neondb_owner
--

SELECT pg_catalog.setval('public.bank_accounts_id_seq', 1, false);


--
-- Name: cards_id_seq; Type: SEQUENCE SET; Schema: public; Owner: neondb_owner
--

SELECT pg_catalog.setval('public.cards_id_seq', 1, false);


--
-- Name: categories_id_seq; Type: SEQUENCE SET; Schema: public; Owner: neondb_owner
--

SELECT pg_catalog.setval('public.categories_id_seq', 8, true);


--
-- Name: expenses_id_seq; Type: SEQUENCE SET; Schema: public; Owner: neondb_owner
--

SELECT pg_catalog.setval('public.expenses_id_seq', 2, true);


--
-- Name: login_attempts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: neondb_owner
--

SELECT pg_catalog.setval('public.login_attempts_id_seq', 1, true);


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: neondb_owner
--

SELECT pg_catalog.setval('public.users_id_seq', 2, true);


--
-- Name: bank_accounts bank_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.bank_accounts
    ADD CONSTRAINT bank_accounts_pkey PRIMARY KEY (id);


--
-- Name: cards cards_pkey; Type: CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.cards
    ADD CONSTRAINT cards_pkey PRIMARY KEY (id);


--
-- Name: categories categories_name_key; Type: CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_name_key UNIQUE (name);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: expenses expenses_pkey; Type: CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_pkey PRIMARY KEY (id);


--
-- Name: login_attempts login_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.login_attempts
    ADD CONSTRAINT login_attempts_pkey PRIMARY KEY (id);


--
-- Name: sessions sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_pkey PRIMARY KEY (id);


--
-- Name: user_passwords user_passwords_pkey; Type: CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.user_passwords
    ADD CONSTRAINT user_passwords_pkey PRIMARY KEY (user_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_bank_accounts_user_id; Type: INDEX; Schema: public; Owner: neondb_owner
--

CREATE INDEX idx_bank_accounts_user_id ON public.bank_accounts USING btree (user_id);


--
-- Name: idx_cards_user_id; Type: INDEX; Schema: public; Owner: neondb_owner
--

CREATE INDEX idx_cards_user_id ON public.cards USING btree (user_id);


--
-- Name: idx_expenses_category_id; Type: INDEX; Schema: public; Owner: neondb_owner
--

CREATE INDEX idx_expenses_category_id ON public.expenses USING btree (category_id);


--
-- Name: idx_expenses_date; Type: INDEX; Schema: public; Owner: neondb_owner
--

CREATE INDEX idx_expenses_date ON public.expenses USING btree (date);


--
-- Name: idx_expenses_user_id; Type: INDEX; Schema: public; Owner: neondb_owner
--

CREATE INDEX idx_expenses_user_id ON public.expenses USING btree (user_id);


--
-- Name: idx_login_attempts_email_time; Type: INDEX; Schema: public; Owner: neondb_owner
--

CREATE INDEX idx_login_attempts_email_time ON public.login_attempts USING btree (lower((email)::text), attempted_at DESC) WHERE (success = false);


--
-- Name: idx_login_attempts_time; Type: INDEX; Schema: public; Owner: neondb_owner
--

CREATE INDEX idx_login_attempts_time ON public.login_attempts USING btree (attempted_at) WHERE (attempted_at IS NOT NULL);


--
-- Name: idx_sessions_expires_at; Type: INDEX; Schema: public; Owner: neondb_owner
--

CREATE INDEX idx_sessions_expires_at ON public.sessions USING btree (expires_at);


--
-- Name: idx_sessions_user_id; Type: INDEX; Schema: public; Owner: neondb_owner
--

CREATE INDEX idx_sessions_user_id ON public.sessions USING btree (user_id);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: neondb_owner
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- Name: bank_accounts bank_accounts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.bank_accounts
    ADD CONSTRAINT bank_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: cards cards_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.cards
    ADD CONSTRAINT cards_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: expenses expenses_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id);


--
-- Name: expenses expenses_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: sessions sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_passwords user_passwords_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: neondb_owner
--

ALTER TABLE ONLY public.user_passwords
    ADD CONSTRAINT user_passwords_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: DEFAULT PRIVILEGES FOR SEQUENCES; Type: DEFAULT ACL; Schema: public; Owner: cloud_admin
--

ALTER DEFAULT PRIVILEGES FOR ROLE cloud_admin IN SCHEMA public GRANT ALL ON SEQUENCES TO neon_superuser WITH GRANT OPTION;


--
-- Name: DEFAULT PRIVILEGES FOR TABLES; Type: DEFAULT ACL; Schema: public; Owner: cloud_admin
--

ALTER DEFAULT PRIVILEGES FOR ROLE cloud_admin IN SCHEMA public GRANT ALL ON TABLES TO neon_superuser WITH GRANT OPTION;


--
-- PostgreSQL database dump complete
--

\unrestrict b7kmrAcMtnvcIBwH3kmW3KLyvtobAtk9CSseEABnsa2UDA7lgH8Uwzmm4X65mSW

