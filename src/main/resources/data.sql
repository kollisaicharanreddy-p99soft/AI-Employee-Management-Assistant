-- Seed departments
INSERT INTO department (name, description) VALUES ('Engineering', 'Software Engineering Team');
INSERT INTO department (name, description) VALUES ('Human Resources', 'HR and People Operations');
INSERT INTO department (name, description) VALUES ('Finance', 'Finance and Accounting');
INSERT INTO department (name, description) VALUES ('Marketing', 'Marketing and Brand');

-- Seed employees
INSERT INTO employee (first_name, last_name, email, phone, job_title, salary, hire_date, status, department_id)
VALUES ('Alice', 'Johnson', 'alice.johnson@example.com', '555-0101', 'Senior Engineer', 95000.00, '2021-03-15', 'ACTIVE', 1);

INSERT INTO employee (first_name, last_name, email, phone, job_title, salary, hire_date, status, department_id)
VALUES ('Bob', 'Smith', 'bob.smith@example.com', '555-0102', 'HR Manager', 75000.00, '2020-07-01', 'ACTIVE', 2);

INSERT INTO employee (first_name, last_name, email, phone, job_title, salary, hire_date, status, department_id)
VALUES ('Carol', 'Williams', 'carol.williams@example.com', '555-0103', 'Financial Analyst', 70000.00, '2022-01-10', 'ACTIVE', 3);

INSERT INTO employee (first_name, last_name, email, phone, job_title, salary, hire_date, status, department_id)
VALUES ('David', 'Brown', 'david.brown@example.com', '555-0104', 'Backend Engineer', 88000.00, '2019-11-20', 'ACTIVE', 1);

INSERT INTO employee (first_name, last_name, email, phone, job_title, salary, hire_date, status, department_id)
VALUES ('Eve', 'Davis', 'eve.davis@example.com', '555-0105', 'Marketing Lead', 80000.00, '2023-05-05', 'ACTIVE', 4);
